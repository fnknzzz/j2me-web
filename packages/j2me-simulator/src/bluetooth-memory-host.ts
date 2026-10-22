import type {
  J2MEBluetoothCapabilities,
  J2MEBluetoothChannelOptions,
  J2MEBluetoothDiscoveredService,
  J2MEBluetoothDiscoveryQuery,
  J2MEBluetoothHost,
  J2MEBluetoothPacketChannel,
  J2MEBluetoothServiceDefinition,
  J2MEBluetoothServiceEndpoint,
  J2MEBluetoothServiceListener,
} from './bluetooth';

interface PublishedService {
  owner: InMemoryBluetoothHost;
  definition: J2MEBluetoothServiceDefinition;
  listener: MemoryServiceListener;
  endpoint: J2MEBluetoothServiceEndpoint;
  psm: number;
}

export class InMemoryBluetoothHub {
  private nextServiceId = 1;
  private readonly services = new Map<string, PublishedService>();

  createHost(capabilities: J2MEBluetoothCapabilities): InMemoryBluetoothHost {
    return new InMemoryBluetoothHost(this, capabilities);
  }

  discover(
    requester: InMemoryBluetoothHost,
    query: J2MEBluetoothDiscoveryQuery,
  ): J2MEBluetoothDiscoveredService[] {
    const uuidFilter = query.serviceUuids?.map(normalizeUuid);
    return Array.from(this.services.values())
      .filter((service) => service.owner !== requester)
      .filter(
        (service) =>
          !query.peerId || service.owner.capabilities.peerId === query.peerId,
      )
      .filter(
        (service) =>
          !uuidFilter?.length ||
          uuidFilter.includes(normalizeUuid(service.definition.serviceUuid)),
      )
      .map((service) => ({
        peerId: service.owner.capabilities.peerId,
        peerName: service.owner.capabilities.name,
        deviceClass: service.owner.capabilities.deviceClass,
        serviceUuid: service.definition.serviceUuid,
        endpoint: service.endpoint,
        psm: service.psm,
        attributes: service.definition.attributes?.map((attribute) => ({
          ...attribute,
        })),
      }));
  }

  publish(
    owner: InMemoryBluetoothHost,
    definition: J2MEBluetoothServiceDefinition,
  ): MemoryServiceListener {
    const serviceId = String(this.nextServiceId++);
    const endpoint = { transport: 'memory', token: serviceId };
    const psm = 0x1001 | ((Number(serviceId) << 1) & 0xeeee);
    const listener = new MemoryServiceListener(this, serviceId, endpoint, psm);
    this.services.set(serviceId, {
      owner,
      definition,
      listener,
      endpoint,
      psm,
    });
    return listener;
  }

  connect(
    client: InMemoryBluetoothHost,
    endpoint: J2MEBluetoothServiceEndpoint,
    options: J2MEBluetoothChannelOptions,
  ): J2MEBluetoothPacketChannel {
    if (endpoint.transport !== 'memory')
      throw new Error('Unsupported Bluetooth transport');
    const service = this.services.get(endpoint.token);
    if (!service) throw new Error('Bluetooth service is no longer available');
    const clientChannel = new MemoryPacketChannel(
      service.owner.capabilities.peerId,
      options.receiveMtu,
      Math.min(options.transmitMtu, service.definition.receiveMtu),
    );
    const serverChannel = new MemoryPacketChannel(
      client.capabilities.peerId,
      service.definition.receiveMtu,
      Math.min(service.definition.transmitMtu, options.receiveMtu),
    );
    clientChannel.pairWith(serverChannel);
    serverChannel.pairWith(clientChannel);
    service.listener.enqueue(serverChannel);
    return clientChannel;
  }

  unpublish(serviceId: string): void {
    this.services.delete(serviceId);
  }
}

export class InMemoryBluetoothHost implements J2MEBluetoothHost {
  constructor(
    private readonly hub: InMemoryBluetoothHub,
    readonly capabilities: J2MEBluetoothCapabilities,
  ) {}

  getCapabilities(): J2MEBluetoothCapabilities {
    return this.capabilities;
  }
  discoverServices(
    query: J2MEBluetoothDiscoveryQuery,
  ): J2MEBluetoothDiscoveredService[] {
    return this.hub.discover(this, query);
  }
  async publishService(
    definition: J2MEBluetoothServiceDefinition,
  ): Promise<J2MEBluetoothServiceListener> {
    return this.hub.publish(this, definition);
  }
  async connect(
    endpoint: J2MEBluetoothServiceEndpoint,
    options: J2MEBluetoothChannelOptions,
  ): Promise<J2MEBluetoothPacketChannel> {
    return this.hub.connect(this, endpoint, options);
  }
}

class MemoryServiceListener implements J2MEBluetoothServiceListener {
  private readonly pending: J2MEBluetoothPacketChannel[] = [];
  private readonly waiters: Array<{
    resolve: (channel: J2MEBluetoothPacketChannel) => void;
    reject: (error: Error) => void;
  }> = [];
  private closed = false;

  constructor(
    private readonly hub: InMemoryBluetoothHub,
    private readonly serviceId: string,
    readonly endpoint: J2MEBluetoothServiceEndpoint,
    readonly psm: number,
  ) {}

  accept(): Promise<J2MEBluetoothPacketChannel> {
    if (this.closed)
      return Promise.reject(new Error('Bluetooth listener is closed'));
    const channel = this.pending.shift();
    if (channel) return Promise.resolve(channel);
    return new Promise((resolve, reject) =>
      this.waiters.push({ resolve, reject }),
    );
  }
  enqueue(channel: J2MEBluetoothPacketChannel): void {
    const waiter = this.waiters.shift();
    if (waiter) waiter.resolve(channel);
    else this.pending.push(channel);
  }
  close(): void {
    if (this.closed) return;
    this.closed = true;
    this.hub.unpublish(this.serviceId);
    for (const channel of this.pending) void channel.close();
    this.pending.length = 0;
    for (const waiter of this.waiters.splice(0)) {
      waiter.reject(new Error('Bluetooth listener is closed'));
    }
  }
}

class MemoryPacketChannel implements J2MEBluetoothPacketChannel {
  private peer?: MemoryPacketChannel;
  private readonly listeners = new Set<(packet: Uint8Array) => void>();
  private closed = false;

  constructor(
    readonly peerId: string,
    readonly receiveMtu: number,
    readonly transmitMtu: number,
  ) {}

  pairWith(peer: MemoryPacketChannel): void {
    this.peer = peer;
  }
  send(packet: Uint8Array): void {
    if (this.closed || !this.peer || this.peer.closed)
      throw new Error('Bluetooth channel is closed');
    if (packet.length > this.transmitMtu)
      throw new Error('Packet exceeds transmit MTU');
    const copy = packet.slice();
    queueMicrotask(() => this.peer?.deliver(copy));
  }
  onPacket(listener: (packet: Uint8Array) => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }
  close(): void {
    if (this.closed) return;
    this.closed = true;
    const peer = this.peer;
    this.peer = undefined;
    if (peer && !peer.closed) peer.close();
    this.listeners.clear();
  }
  private deliver(packet: Uint8Array): void {
    if (this.closed || packet.length > this.receiveMtu) return;
    for (const listener of this.listeners) listener(packet);
  }
}

const normalizeUuid = (uuid: string): string =>
  uuid.replace(/-/g, '').toLowerCase();
