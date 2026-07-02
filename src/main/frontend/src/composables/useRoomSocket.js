import { Client } from '@stomp/stompjs';

export function useRoomSocket({ onEvent, onDisconnect }) {
  let client = null;
  let subscription = null;

  function websocketUrl() {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    return protocol + '//' + window.location.host + '/ws';
  }

  function connect(roomCode) {
    disconnect();
    client = new Client({
      brokerURL: websocketUrl(),
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      debug: () => {},
    });

    client.onConnect = () => {
      subscription = client.subscribe('/topic/rooms/' + encodeURIComponent(roomCode) + '/events', (message) => {
        try {
          onEvent(JSON.parse(message.body));
        } catch {
          onEvent(null);
        }
      });
    };

    client.onWebSocketClose = () => {
      onDisconnect?.();
    };

    client.activate();
  }

  function publishChat(roomCode, playerToken, content) {
    if (!client?.connected) throw new Error('连接已断开');
    client.publish({
      destination: '/app/rooms/' + encodeURIComponent(roomCode) + '/chat',
      body: JSON.stringify({ playerToken, content }),
    });
  }

  function disconnect() {
    if (subscription) {
      subscription.unsubscribe();
      subscription = null;
    }
    if (client) {
      client.deactivate();
      client = null;
    }
  }

  return { connect, publishChat, disconnect };
}
