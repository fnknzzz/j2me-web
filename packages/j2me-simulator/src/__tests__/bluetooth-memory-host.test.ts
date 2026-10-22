import { describe, expect, it, vi } from 'vitest';
import { BluetoothHostBridge } from '../backend/bluetooth-host-bridge';
import { InMemoryBluetoothHub } from '../bluetooth-memory-host';

describe('InMemoryBluetoothHub', () => {
  it('discovers a published service and preserves packet boundaries', async () => {
    const hub = new InMemoryBluetoothHub();
    const server = hub.createHost({ peerId: 'server', name: 'Server' });
    const client = hub.createHost({ peerId: 'client', name: 'Client' });
    const listener = await server.publishService({
      serviceUuid: '00112233',
      serviceName: 'Art of War',
      receiveMtu: 288,
      transmitMtu: 288,
      attributes: [{ id: 0x4321, type: 0x20, value: 'Peru;war=2' }],
    });

    const services = await client.discoverServices({
      serviceUuids: ['00112233'],
    });
    expect(services).toHaveLength(1);
    expect(services[0]).toMatchObject({ peerId: 'server', peerName: 'Server' });
    expect(services[0].attributes).toEqual([
      { id: 0x4321, type: 0x20, value: 'Peru;war=2' },
    ]);

    const accept = listener.accept();
    const clientChannel = await client.connect(services[0].endpoint, {
      receiveMtu: 288,
      transmitMtu: 288,
    });
    const serverChannel = await accept;
    const onServerPacket = vi.fn();
    const onClientPacket = vi.fn();
    serverChannel.onPacket(onServerPacket);
    clientChannel.onPacket(onClientPacket);

    await clientChannel.send(new Uint8Array([1, 2, 3]));
    await serverChannel.send(new Uint8Array([4, 5]));
    await Promise.resolve();

    expect(onServerPacket).toHaveBeenCalledOnce();
    expect(onServerPacket).toHaveBeenCalledWith(new Uint8Array([1, 2, 3]));
    expect(onClientPacket).toHaveBeenCalledOnce();
    expect(onClientPacket).toHaveBeenCalledWith(new Uint8Array([4, 5]));
  });

  it('rejects packets larger than the negotiated transmit MTU', async () => {
    const hub = new InMemoryBluetoothHub();
    const server = hub.createHost({ peerId: 'server', name: 'Server' });
    const client = hub.createHost({ peerId: 'client', name: 'Client' });
    const listener = await server.publishService({
      serviceUuid: 'abcd',
      receiveMtu: 48,
      transmitMtu: 48,
    });
    const [service] = await client.discoverServices({ serviceUuids: ['ABCD'] });
    const channel = await client.connect(service.endpoint, {
      receiveMtu: 48,
      transmitMtu: 64,
    });
    await listener.accept();

    expect(() => channel.send(new Uint8Array(49))).toThrow(
      'Packet exceeds transmit MTU',
    );
  });

  it('rejects a pending accept when the listener closes', async () => {
    const hub = new InMemoryBluetoothHub();
    const server = hub.createHost({ peerId: 'server', name: 'Server' });
    const listener = await server.publishService({
      serviceUuid: 'closed',
      receiveMtu: 48,
      transmitMtu: 48,
    });
    const accepting = listener.accept();
    await listener.close();
    await expect(accepting).rejects.toThrow('Bluetooth listener is closed');
  });

  it('passes escaped SDP attributes through the coarse host bridge', async () => {
    const hub = new InMemoryBluetoothHub();
    const serverBridge = new BluetoothHostBridge(
      hub.createHost({ peerId: 'server', name: 'Server' }),
    );
    const clientBridge = new BluetoothHostBridge(
      hub.createHost({ peerId: 'client', name: 'Client' }),
    );

    const published = await serverBridge.request(
      'publish',
      '00112233\tArt of War\t288\t288\t17185%3D32%3DPeru%253Bwar%253D2',
    );
    expect(published).toMatch(/^OK\t/);

    const discovered = await clientBridge.request('discover', '\t00112233');
    expect(discovered).toContain('\t17185%3D32%3DPeru%253Bwar%253D2');

    await serverBridge.close();
    await clientBridge.close();
  });
});
