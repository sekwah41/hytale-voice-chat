 export type PeerEntry = {
    id: string;
    state: string;
};

type PeerConnectionEntry = {
    pc: RTCPeerConnection;
    pipeline: AudioPipeline | null;
};

type PeerListUpdater = (prev: PeerEntry[]) => PeerEntry[];

type VoiceChatCallbacks = {
    onStatus: (text: string) => void;
    onConnectionStatus: (text: string) => void;
    onMicStatus: (text: string) => void;
    onJoinDisabled: (disabled: boolean) => void;
    onMuteDisabled: (disabled: boolean) => void;
    onMuted: (muted: boolean) => void;
    onPeerId: (id: string) => void;
    onUserName: (name: string) => void;
    onPttActive: (active: boolean) => void;
    onPeerListUpdate: (updater: PeerListUpdater) => void;
};

type Vector3 = {
    x: number;
    y: number;
    z: number;
};

type PeerData = {
    position: Vector3 | null;
    rotation: Vector3 | null;
    // This will be for future tracking
    filters: Record<string, unknown>;
};

type AudioFilter = {
    id: string;
    create: (context: AudioContext) => AudioNode;
    update?: (
        node: AudioNode,
        peerId: string,
        peerData: PeerData | undefined,
        selfPosition: Vector3 | null,
        config: VoiceChatConfig | null,
    ) => void;
    cleanup?: (node: AudioNode) => void;
};

type AudioPipeline = {
    source: AudioNode;
    filters: { id: string; node: AudioNode }[];
};

type VoiceChatConfig = {
    fullVolumeRange: number;
    fallOffRange: number;
    additionalPeerConnectionRange: number;
};

const normalizeError = (error: unknown) =>
    error instanceof Error ? error : new Error('Voice chat error.');

export class VoiceChatController {
    private callbacks: VoiceChatCallbacks;
    private state = {
        ws: null as WebSocket | null,
        id: null as string | null,
        localStream: null as MediaStream | null,
        audioContext: null as AudioContext | null,
        peers: new Map<string, PeerConnectionEntry>(),
        peerData: new Map<string, PeerData>(),
        debugAudio: null as { peerId: string; audio: HTMLAudioElement; pipeline: AudioPipeline } | null,
        muted: false,
        pttEnabled: false,
        pttActive: false,
        position: null as Vector3 | null,
        rotation: null as Vector3 | null,
        config: null as VoiceChatConfig | null,
    };
    private audioFilters: AudioFilter[] = [
        {
            id: 'stereo-pan',
            create: (context) => new StereoPannerNode(context, { pan: 0 }),
            update: (node, peerId, peerData, selfPosition, config) => {
                if (!(node instanceof StereoPannerNode)) {
                    return;
                }
                const panValue = this.calculateStereoPan(peerId, peerData, selfPosition, config);
                this.smoothParam(node.pan, panValue);
            },
        },
        {
            id: 'distance-gain',
            create: (context) => context.createGain(),
            update: (node, peerId, peerData, selfPosition, config) => {
                if (!(node instanceof GainNode)) {
                    return;
                }
                const gainValue = this.calculateDistanceGain(peerId, peerData, selfPosition, config);
                this.smoothParam(node.gain, gainValue);
                console.debug('[voicechat] gain update', peerId, gainValue);
            },
        },
    ];

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
            this.ensureAudioContext();
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
            this.teardownPipeline(entry.pipeline);
        });
        this.state.peers.clear();
        this.stopDebugAudio();
        this.state.audioContext?.close();
        this.state.audioContext = null;
    };

    startDebugAudio = (url: string, position: Vector3) => {
        this.stopDebugAudio();
        const peerId = 'debug-audio';
        const audio = new Audio(url);
        audio.loop = true;
        audio.volume = 1;
        const pipeline = this.createAudioPipeline(peerId, audio);
        if (!pipeline) {
            return;
        }
        const data = this.getPeerData(peerId);
        data.position = position;
        this.state.debugAudio = { peerId, audio, pipeline };
        audio.play().catch(() => {
            // Autoplay can be blocked; keep it ready for user interaction.
        });
    };

    stopDebugAudio = () => {
        const debugAudio = this.state.debugAudio;
        if (!debugAudio) {
            return;
        }
        debugAudio.audio.pause();
        debugAudio.audio.src = '';
        this.teardownPipeline(debugAudio.pipeline);
        this.state.debugAudio = null;
        this.state.peerData.delete(debugAudio.peerId);
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
            if (!entry.pipeline) {
                entry.pipeline = this.createAudioPipeline(peerId, event.streams[0]);
            }
        };

        return pc;
    };

    private addPeer = (peerId: string) => {
        if (this.state.peers.has(peerId)) {
            return this.state.peers.get(peerId)!;
        }
        const pc = this.createPeerConnection(peerId);
        this.state.peers.set(peerId, { pc, pipeline: null });
        this.getPeerData(peerId);
        this.addPeerListItem(peerId);
        return this.state.peers.get(peerId)!;
    };

    private makeOffer = async (peerId: string, pc: RTCPeerConnection) => {
        const offer = await pc.createOffer();
        await pc.setLocalDescription(offer);
        this.sendMessage({ type: 'offer', to: peerId, sdp: pc.localDescription });
    };

    private handleWelcome = (message: {
        id?: string;
        peers?: string[];
        userName?: string;
        config?: VoiceChatConfig;
    }) => {
        this.state.id = message.id ?? null;
        this.callbacks.onPeerId(this.state.id ?? '');
        this.callbacks.onStatus('Joined. Waiting for peers...');
        this.callbacks.onMuteDisabled(false);
        this.callbacks.onUserName(message.userName ?? '');
        this.state.config = message.config ?? null;
        this.updateAllPeerFilters();

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
        this.teardownPipeline(entry.pipeline);
        this.state.peers.delete(peerId);
        this.state.peerData.delete(peerId);
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

    private handlePosition = (message: { id?: string; position?: Vector3 }) => {
        if (!message.position) {
            return;
        }
        if (message.id) {
            const data = this.getPeerData(message.id);
            data.position = message.position;
            this.updatePeerFilters(message.id);
        }
        if (message.id && this.state.id && message.id !== this.state.id) {
            console.debug('[voicechat] peer position update', message.id, message.position);
            return;
        }
        this.state.position = message.position;
        this.updateAllPeerFilters();
        console.debug('[voicechat] position update', message.position);
    };

    private handleRotation = (message: { id?: string; rotation?: Vector3 }) => {
        if (!message.rotation) {
            return;
        }
        if (message.id) {
            const data = this.getPeerData(message.id);
            data.rotation = message.rotation;
            this.updatePeerFilters(message.id);
        }
        if (message.id && this.state.id && message.id !== this.state.id) {
            console.debug('[voicechat] peer rotation update', message.id, message.rotation);
            return;
        }
        this.state.rotation = message.rotation;
        this.updateAllPeerFilters();
        console.debug('[voicechat] rotation update', message.rotation);
    };

    private getPeerData = (peerId: string) => {
        let data = this.state.peerData.get(peerId);
        if (!data) {
            data = { position: null, rotation: null, filters: {} };
            this.state.peerData.set(peerId, data);
        }
        return data;
    };

    private ensureAudioContext = () => {
        if (!this.state.audioContext) {
            this.state.audioContext = new AudioContext();
        }
        if (this.state.audioContext.state === 'suspended') {
            this.state.audioContext.resume().catch(() => {
                // Resume may fail if not called from a user gesture.
            });
        }
        return this.state.audioContext;
    };

    private createAudioPipeline = (peerId: string, sourceInput: MediaStream | HTMLMediaElement) => {
        const context = this.ensureAudioContext();
        if (!context) {
            return null;
        }
        const source =
            sourceInput instanceof MediaStream
                ? context.createMediaStreamSource(sourceInput)
                : context.createMediaElementSource(sourceInput);
        let current: AudioNode = source;
        const filterNodes = this.audioFilters.map((filter) => {
            const node = filter.create(context);
            current.connect(node);
            current = node;
            return { id: filter.id, node };
        });
        current.connect(context.destination);
        const pipeline = { source, filters: filterNodes };
        this.updatePeerFilters(peerId);
        return pipeline;
    };

    private teardownPipeline = (pipeline: AudioPipeline | null) => {
        if (!pipeline) {
            return;
        }
        pipeline.filters.forEach((filterNode) => {
            const filter = this.audioFilters.find((entry) => entry.id === filterNode.id);
            filter?.cleanup?.(filterNode.node);
            filterNode.node.disconnect();
        });
        pipeline.source.disconnect();
    };

    private updatePeerFilters = (peerId: string) => {
        const pipeline = this.getPipelineForPeer(peerId);
        if (!pipeline) {
            return;
        }
        const peerData = this.state.peerData.get(peerId);
        this.audioFilters.forEach((filter, index) => {
            const node = pipeline.filters[index]?.node;
            if (!node || !filter.update) {
                return;
            }
            filter.update(node, peerId, peerData, this.state.position, this.state.config);
        });
    };

    private updateAllPeerFilters = () => {
        this.state.peers.forEach((_, peerId) => {
            this.updatePeerFilters(peerId);
        });
        if (this.state.debugAudio) {
            this.updatePeerFilters(this.state.debugAudio.peerId);
        }
    };

    private getPipelineForPeer = (peerId: string) => {
        const entry = this.state.peers.get(peerId);
        if (entry?.pipeline) {
            return entry.pipeline;
        }
        if (this.state.debugAudio?.peerId === peerId) {
            return this.state.debugAudio.pipeline;
        }
        return null;
    };

    private calculateDistanceGain = (
        peerId: string,
        peerData: PeerData | undefined,
        selfPosition: Vector3 | null,
        config: VoiceChatConfig | null,
    ) => {
        if (!peerData?.position || !selfPosition || peerId === this.state.id) {
            return 1;
        }
        const fullVolumeRange = config?.fullVolumeRange ?? 12;
        const fallOffRange = Math.max(0.0001, config?.fallOffRange ?? 16);
        const dx = peerData.position.x - selfPosition.x;
        const dy = peerData.position.y - selfPosition.y;
        const dz = peerData.position.z - selfPosition.z;
        const distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        console.debug('[voicechat] distance', peerId, distance);
        if (distance <= fullVolumeRange) {
            return 1;
        }
        if (distance >= fullVolumeRange + fallOffRange) {
            return 0;
        }
        const fade = (distance - fullVolumeRange) / fallOffRange;
        return Math.max(0, Math.min(1, 1 - fade));
    };

    private calculateStereoPan = (
        peerId: string,
        peerData: PeerData | undefined,
        selfPosition: Vector3 | null,
        _config: VoiceChatConfig | null,
    ) => {
        if (!peerData?.position || !selfPosition || peerId === this.state.id) {
            return 0;
        }
        const selfYaw = this.state.rotation?.y;
        if (selfYaw == null) {
            return 0;
        }
        const dx = peerData.position.x - selfPosition.x;
        const dz = peerData.position.z - selfPosition.z;
        const angleToPeer = Math.atan2(dx, dz);
        const relativeYaw = this.normalizeAngle(angleToPeer - selfYaw);
        const pan = Math.sin(relativeYaw);
        const clamped = Math.max(-1, Math.min(1, pan));
        return clamped * 0.8;
    };

    private smoothParam = (param: AudioParam, value: number) => {
        const context = this.state.audioContext;
        if (!context) {
            param.value = value;
            return;
        }
        const now = context.currentTime;
        param.setTargetAtTime(value, now, 0.05);
    };

    private normalizeAngle = (angle: number) => {
        let result = angle;
        while (result > Math.PI) {
            result -= Math.PI * 2;
        }
        while (result < -Math.PI) {
            result += Math.PI * 2;
        }
        return result;
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
            this.callbacks.onStatus('Connected.');
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
                    this.handleWelcome(message as { id?: string; peers?: string[]; userName?: string });
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
                case 'position':
                    this.handlePosition(message as { id?: string; position?: Vector3 });
                    break;
                case 'rotation':
                    this.handleRotation(message as { id?: string; rotation?: Vector3 });
                    break;
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
            this.callbacks.onUserName('');
        });
    };
}
