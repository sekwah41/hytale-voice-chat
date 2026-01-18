 export type PeerEntry = {
    id: string;
    state: string;
};

type PeerConnectionEntry = {
    pc: RTCPeerConnection;
    audio: HTMLAudioElement | null;
};

type PeerListUpdater = (prev: PeerEntry[]) => PeerEntry[];

type VoiceChatCallbacks = {
    onStatus: (text: string) => void;
    onConnectionStatus: (text: string) => void;
    onMicStatus: (text: string) => void;
    onJoinDisabled: (disabled: boolean) => void;
    onMuteDisabled: (disabled: boolean) => void;
    onMuted: (muted: boolean) => void;
    onPttActive: (active: boolean) => void;
    onPeerListUpdate: (updater: PeerListUpdater) => void;
};

const normalizeError = (error: unknown) =>
    error instanceof Error ? error : new Error('Voice chat error.');

export class VoiceChatController {
    private callbacks: VoiceChatCallbacks;
    private state = {
        ws: null as WebSocket | null,
        id: null as string | null,
        localStream: null as MediaStream | null,
        peers: new Map<string, PeerConnectionEntry>(),
        muted: false,
        pttEnabled: false,
        pttActive: false,
    };

    constructor(callbacks: VoiceChatCallbacks) {
        this.callbacks = callbacks;
    }

    startVoice = async (token: string | null) => {
        try {
            if (!token) {
                throw new Error('Missing token. Run /voice in-game to get a link.');
            }
            this.callbacks.onJoinDisabled(true);
            this.callbacks.onStatus('Requesting microphone permission...');
            const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
            this.state.localStream = stream;
            this.callbacks.onMicStatus('Granted');
            this.connectWebSocket(token);
        } catch (error) {
            this.handleError(normalizeError(error));
        }
    };

    toggleMute = () => {
        const nextMuted = !this.state.muted;
        this.state.muted = nextMuted;
        this.callbacks.onMuted(nextMuted);
        this.updateTrackState();
        this.sendMessage({ type: 'mute', muted: nextMuted });
    };

    togglePttMode = () => {
        const nextEnabled = !this.state.pttEnabled;
        this.state.pttEnabled = nextEnabled;
        this.state.pttActive = false;
        this.callbacks.onPttActive(false);
        this.updateTrackState();
        this.sendMessage({ type: 'ptt', active: false });
    };

    destroy = () => {
        this.state.ws?.close();
        if (this.state.localStream) {
            this.state.localStream.getTracks().forEach((track) => track.stop());
        }
        this.state.peers.forEach((entry) => {
            entry.pc.close();
            if (entry.audio) {
                entry.audio.remove();
            }
        });
        this.state.peers.clear();
    };

    private sendMessage = (message: Record<string, unknown>) => {
        const ws = this.state.ws;
        if (ws && ws.readyState === WebSocket.OPEN) {
            ws.send(JSON.stringify(message));
        }
    };

    private updatePeerState = (peerId: string, stateLabel: string) => {
        this.callbacks.onPeerListUpdate((prev) =>
            prev.map((peer) => (peer.id === peerId ? { ...peer, state: stateLabel } : peer)),
        );
    };

    private addPeerListItem = (peerId: string) => {
        this.callbacks.onPeerListUpdate((prev) => {
            if (prev.some((peer) => peer.id === peerId)) {
                return prev;
            }
            return [...prev, { id: peerId, state: 'Idle' }];
        });
    };

    private removePeerListItem = (peerId: string) => {
        this.callbacks.onPeerListUpdate((prev) => prev.filter((peer) => peer.id !== peerId));
    };

    private updateTrackState = () => {
        const { localStream, muted, pttEnabled, pttActive } = this.state;
        if (!localStream) {
            return;
        }
        const talkEnabled = !muted && (!pttEnabled || pttActive);
        localStream.getAudioTracks().forEach((track) => {
            track.enabled = talkEnabled;
        });
        this.callbacks.onStatus(talkEnabled ? 'Mic live.' : 'Mic muted.');
    };

    private handleError = (error: Error) => {
        this.callbacks.onStatus(error.message);
        this.callbacks.onConnectionStatus('Error');
        this.callbacks.onJoinDisabled(false);
        this.callbacks.onMuteDisabled(true);
    };

    private createPeerConnection = (peerId: string) => {
        const pc = new RTCPeerConnection({
            iceServers: [{ urls: 'stun:stun.l.google.com:19302' }],
        });

        if (this.state.localStream) {
            this.state.localStream.getTracks().forEach((track) => {
                pc.addTrack(track, this.state.localStream!);
            });
        }

        pc.onicecandidate = (event) => {
            if (event.candidate) {
                this.sendMessage({ type: 'ice', to: peerId, candidate: event.candidate });
            }
        };

        pc.ontrack = (event) => {
            const entry = this.state.peers.get(peerId);
            if (!entry) {
                return;
            }
            if (!entry.audio) {
                const audio = document.createElement('audio');
                audio.autoplay = true;
                audio.srcObject = event.streams[0];
                audio.dataset.peer = peerId;
                document.body.appendChild(audio);
                entry.audio = audio;
            }
        };

        return pc;
    };

    private addPeer = (peerId: string) => {
        if (this.state.peers.has(peerId)) {
            return this.state.peers.get(peerId)!;
        }
        const pc = this.createPeerConnection(peerId);
        this.state.peers.set(peerId, { pc, audio: null });
        this.addPeerListItem(peerId);
        return this.state.peers.get(peerId)!;
    };

    private makeOffer = async (peerId: string, pc: RTCPeerConnection) => {
        const offer = await pc.createOffer();
        await pc.setLocalDescription(offer);
        this.sendMessage({ type: 'offer', to: peerId, sdp: pc.localDescription });
    };

    private handleWelcome = (message: { id?: string; peers?: string[] }) => {
        this.state.id = message.id ?? null;
        this.callbacks.onStatus('Joined. Waiting for peers...');
        this.callbacks.onMuteDisabled(false);

        const peers = Array.isArray(message.peers) ? message.peers : [];
        peers.forEach((peerId) => {
            if (peerId !== this.state.id) {
                this.addPeer(peerId);
            }
        });

        if (peers.length > 0) {
            this.callbacks.onStatus('Connected. Negotiating audio...');
        }
    };

    private handlePeerJoin = async (message: { id?: string }) => {
        const peerId = message.id;
        if (!peerId || peerId === this.state.id) {
            return;
        }
        const entry = this.addPeer(peerId);
        if (entry) {
            await this.makeOffer(peerId, entry.pc);
        }
    };

    private handlePeerLeave = (message: { id?: string }) => {
        const peerId = message.id;
        if (!peerId) {
            return;
        }
        const entry = this.state.peers.get(peerId);
        if (!entry) {
            return;
        }
        entry.pc.close();
        if (entry.audio) {
            entry.audio.remove();
        }
        this.state.peers.delete(peerId);
        this.removePeerListItem(peerId);
        this.callbacks.onStatus('Peer left.');
    };

    private handleOffer = async (message: { from?: string; sdp?: RTCSessionDescriptionInit }) => {
        const peerId = message.from;
        if (!peerId || !message.sdp) {
            return;
        }
        const entry = this.addPeer(peerId);
        await entry.pc.setRemoteDescription(message.sdp);
        const answer = await entry.pc.createAnswer();
        await entry.pc.setLocalDescription(answer);
        this.sendMessage({ type: 'answer', to: peerId, sdp: entry.pc.localDescription });
    };

    private handleAnswer = async (message: { from?: string; sdp?: RTCSessionDescriptionInit }) => {
        const peerId = message.from;
        if (!peerId || !message.sdp) {
            return;
        }
        const entry = this.state.peers.get(peerId);
        if (!entry) {
            return;
        }
        await entry.pc.setRemoteDescription(message.sdp);
    };

    private handleIce = async (message: { from?: string; candidate?: RTCIceCandidateInit }) => {
        const peerId = message.from;
        if (!peerId || !message.candidate) {
            return;
        }
        const entry = this.state.peers.get(peerId);
        if (!entry) {
            return;
        }
        try {
            await entry.pc.addIceCandidate(message.candidate);
        } catch {
            this.callbacks.onStatus('Failed to add ICE candidate.');
        }
    };

    private connectWebSocket = (token: string) => {
        const params = new URLSearchParams(window.location.search);
        const address = params.get('address') ?? window.location.host;
        const protocol = window.location.protocol === 'http:' ? 'ws' : 'wss';
        const devSocketUrl =
            window.location.origin === 'http://localhost:5173'
                ? 'ws://localhost:24454/voice/ws'
                : null;
        const socketUrl =
            devSocketUrl ??
            (address.startsWith('ws://') || address.startsWith('wss://')
                ? address
                : `${protocol}://${address}/voice/ws`);
        const ws = new WebSocket(socketUrl);
        this.state.ws = ws;

        ws.addEventListener('open', () => {
            this.callbacks.onConnectionStatus('Online');
            this.callbacks.onStatus('Connected. Joining voice...');
            this.sendMessage({ type: 'hello', token });
        });

        ws.addEventListener('message', async (event) => {
            let message: { type?: string } = {};
            try {
                message = JSON.parse(event.data as string);
            } catch {
                return;
            }

            switch (message.type) {
                case 'welcome':
                    this.handleWelcome(message as { id?: string; peers?: string[] });
                    break;
                case 'peer-join':
                    await this.handlePeerJoin(message as { id?: string });
                    break;
                case 'peer-leave':
                    this.handlePeerLeave(message as { id?: string });
                    break;
                case 'offer':
                    await this.handleOffer(
                        message as {
                            from?: string;
                            sdp?: RTCSessionDescriptionInit;
                        },
                    );
                    break;
                case 'answer':
                    await this.handleAnswer(
                        message as {
                            from?: string;
                            sdp?: RTCSessionDescriptionInit;
                        },
                    );
                    break;
                case 'ice':
                    await this.handleIce(
                        message as {
                            from?: string;
                            candidate?: RTCIceCandidateInit;
                        },
                    );
                    break;
                case 'mute': {
                    const payload = message as { id?: string; muted?: boolean };
                    if (payload.id) {
                        this.updatePeerState(payload.id, payload.muted ? 'Muted' : 'Talking');
                    }
                    break;
                }
                case 'ptt': {
                    const payload = message as { id?: string; active?: boolean };
                    if (payload.id) {
                        this.updatePeerState(payload.id, payload.active ? 'PTT' : 'Idle');
                    }
                    break;
                }
                case 'error':
                    this.handleError(
                        new Error((message as { message?: string }).message || 'Voice chat error.'),
                    );
                    break;
                default:
            }
        });

        ws.addEventListener('close', () => {
            this.callbacks.onConnectionStatus('Offline');
            this.callbacks.onStatus('Disconnected.');
            this.callbacks.onJoinDisabled(false);
            this.callbacks.onMuteDisabled(true);
        });
    };
}
