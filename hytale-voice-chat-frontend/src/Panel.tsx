import { useEffect, useRef, useState } from 'react';
import type { PeerEntry } from './voicechat';
import { VoiceChatController } from './voicechat';
import './panel.scss';

function Panel() {
    const [token, setToken] = useState<string | null>(null);
    const [statusText, setStatusText] = useState('Idle. Ready to connect.');
    const [connectionStatus, setConnectionStatus] = useState('Offline');
    const [micStatus, setMicStatus] = useState('Not granted');
    const [joinDisabled, setJoinDisabled] = useState(false);
    const [muteDisabled, setMuteDisabled] = useState(true);
    const [muted, setMuted] = useState(false);
    const [userName, setUserName] = useState('');
    const [peerId, setPeerId] = useState('');
    const [, setPttActive] = useState(false);
    const [peerList, setPeerList] = useState<PeerEntry[]>([]);
    const controllerRef = useRef<VoiceChatController | null>(null);
    const autoStartedRef = useRef(false);
    if (!controllerRef.current) {
        const logStatus = (label: string, value: string) => {
            console.log(`[voicechat] ${label}: ${value}`);
        };
        controllerRef.current = new VoiceChatController({
            onStatus: (text) => {
                logStatus('status', text);
                setStatusText(text);
            },
            onConnectionStatus: (text) => {
                logStatus('connection', text);
                setConnectionStatus(text);
            },
            onMicStatus: (text) => {
                logStatus('mic', text);
                setMicStatus(text);
            },
            onJoinDisabled: setJoinDisabled,
            onMuteDisabled: setMuteDisabled,
            onMuted: setMuted,
            onPeerId: setPeerId,
            onUserName: setUserName,
            onPttActive: setPttActive,
            onPeerListUpdate: setPeerList,
        });
    }

    useEffect(() => {
        const url = new URL(window.location.href);
        const tokenParam = url.searchParams.get('token');
        if (tokenParam) {
            try {
                window.localStorage.setItem('voiceChatToken', tokenParam);
            } catch {
                // Ignore storage failures (private mode, storage blocked).
            }
            url.searchParams.delete('token');
            window.history.replaceState({}, document.title, url.toString());
            setToken(tokenParam);
            return;
        }
        try {
            const storedToken = window.localStorage.getItem('voiceChatToken');
            if (storedToken) {
                setToken(storedToken);
            }
        } catch {
            // Ignore storage failures (private mode, storage blocked).
        }
    }, []);

    useEffect(() => {
        if (!token || autoStartedRef.current) {
            return;
        }
        let cancelled = false;
        navigator.permissions
            .query({ name: 'microphone' as PermissionName })
            .then((result) => {
                if (cancelled || result.state !== 'granted') {
                    return;
                }
                autoStartedRef.current = true;
                controllerRef.current?.startVoice(token);
            })
            .catch(() => {
                // Ignore permission API failures.
            });
        return () => {
            cancelled = true;
        };
    }, [token]);

    useEffect(
        () => () => {
            controllerRef.current?.destroy();
        },
        [],
    );

    const startVoice = () => {
        controllerRef.current?.startVoice(token);
    };

    const toggleMute = () => {
        controllerRef.current?.toggleMute();
    };

    const togglePttMode = () => {
        controllerRef.current?.togglePttMode();
    };

    return (
        <div className="shell">
            <header>
                <p className="title">Hytale Voice Chat</p>
                <p className="subhead">Keep this page open while you play.</p>
            </header>
            <main className="panels">
                <section className="panel">
                    <h2>Session</h2>
                    <p className="status-line">{statusText}</p>
                    <div className="controls">
                        <button className="primary" onClick={startVoice} disabled={joinDisabled}>
                            Join voice
                        </button>
                        <button className="secondary" onClick={toggleMute} disabled={muteDisabled}>
                            {muted ? 'Unmute' : 'Mute'}
                        </button>
                        <button className="secondary" onClick={togglePttMode} disabled={true}>
                            {'Push-to-talk: Disabled'}
                        </button>
                    </div>
                    <div className="hint">
                        <p>
                            Push-to-talk mode is not yet implemented. Once I have the rest working I
                            will look to add a hotkey and an indicator in game if possible.
                        </p>
                    </div>
                </section>
                <section className="panel">
                    <h2>Peers</h2>
                    <p className="mini">Connected peers update automatically.</p>
                    <ul className="peers">
                        {peerList.length ? (
                            peerList.map((peer) => (
                                <li key={peer.id}>
                                    {peer.id.slice(0, 8)}
                                    <span className="state">{peer.state}</span>
                                </li>
                            ))
                        ) : (
                            <li className="placeholder">No peers yet.</li>
                        )}
                    </ul>
                </section>
                <section className="panel">
                    <h2>Diagnostics</h2>
                    <div className="stat">
                        <span>User</span>
                        {userName ? <span className="statValue">{userName}</span> : <span className="statValue statAwaitingValue">Not Set</span>}
                    </div>
                    <div className="stat">
                        <span>Peer ID</span>
                        {peerId ? <span className="statValue">{peerId.slice(0, 8)}</span> : <span className="statValue statAwaitingValue">Not Set</span>}
                    </div>
                    <div className="stat">
                        <span>Token</span>
                        <span className="statValue">{token ? 'Loaded' : 'Missing'}</span>
                    </div>
                    <div className="stat">
                        <span>Connection</span>
                        <span className="statValue">{connectionStatus}</span>
                    </div>
                    <div className="stat">
                        <span>Microphone</span>
                        <span className="statValue">{micStatus}</span>
                    </div>
                </section>
            </main>

            <header>
                <p className="title">
                    Created by{' '}
                    <a href="https://www.sekwah.com/" target="_blank">
                        Sekwah
                    </a>
                </p>
            </header>
        </div>
    );
}

export default Panel;
