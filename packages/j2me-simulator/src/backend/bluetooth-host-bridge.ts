import type {
  J2MEBluetoothDiscoveredService,
  J2MEBluetoothHost,
  J2MEBluetoothPacketChannel,
  J2MEBluetoothServiceAttribute,
  J2MEBluetoothServiceEndpoint,
  J2MEBluetoothServiceListener,
} from '../bluetooth';

interface ChannelState {
  channel: J2MEBluetoothPacketChannel;
  packets: Uint8Array[];
  waiters: Array<(packet: Uint8Array) => void>;
  unsubscribe: () => void;
}

const escapeWire = (value: string): string =>
  value
    .replace(/%/g, '%25')
    .replace(/\t/g, '%09')
    .replace(/\n/g, '%0A')
    .replace(/;/g, '%3B')
    .replace(/=/g, '%3D');
const unescapeWire = (value: string): string =>
  value.replace(/%([0-9a-f]{2})/gi, (_, hex: string) =>
    String.fromCharCode(Number.parseInt(hex, 16)),
  );
const ok = (...fields: Array<string | number>): string =>
  ['OK', ...fields.map((value) => escapeWire(String(value)))].join('\t');
const fail = (error: unknown): string =>
  `ERR\t${escapeWire(error instanceof Error ? error.message : String(error))}`;

export class BluetoothHostBridge {
  private nextHandle = 1;
  private readonly listeners = new Map<number, J2MEBluetoothServiceListener>();
  private readonly channels = new Map<number, ChannelState>();
  private readonly peerToAddress = new Map<string, string>();
  private readonly addressToPeer = new Map<string, string>();

  constructor(private readonly host?: J2MEBluetoothHost) {}

  async request(operation: string, payload: string): Promise<string> {
    if (!this.host) return fail('Bluetooth unavailable');
    try {
      switch (operation) {
        case 'capabilities':
          return this.capabilities();
        case 'discover':
          return this.discover(payload);
        case 'publish':
          return this.publish(payload);
        case 'accept':
          return this.accept(Number(payload));
        case 'connect':
          return this.connect(payload);
        case 'send':
          return this.send(payload);
        case 'ready':
          return this.ready(Number(payload));
        case 'receive':
          return this.receive(Number(payload));
        case 'closeChannel':
          return this.closeChannel(Number(payload));
        case 'closeListener':
          return this.closeListener(Number(payload));
        default:
          return fail(`Unsupported Bluetooth operation: ${operation}`);
      }
    } catch (error) {
      return fail(error);
    }
  }

  async close(): Promise<void> {
    await Promise.all([
      ...Array.from(this.channels.keys(), (id) => this.closeChannel(id)),
      ...Array.from(this.listeners.keys(), (id) => this.closeListener(id)),
    ]);
  }

  private async capabilities(): Promise<string> {
    const capabilities = await this.host!.getCapabilities();
    return ok(this.addressFor(capabilities.peerId), capabilities.name);
  }

  private async discover(payload: string): Promise<string> {
    const [address, uuidList = ''] = payload.split('\t');
    const peerId = address ? this.addressToPeer.get(address) : undefined;
    const services = await this.host!.discoverServices({
      peerId,
      serviceUuids: uuidList ? uuidList.split(',') : undefined,
    });
    return [
      'OK',
      ...services.map((service) => this.encodeService(service)),
    ].join('\n');
  }

  private async publish(payload: string): Promise<string> {
    const [serviceUuid, serviceName, receiveMtu, transmitMtu, attributes = ''] =
      payload.split('\t');
    const listener = await this.host!.publishService({
      serviceUuid,
      serviceName: unescapeWire(serviceName),
      receiveMtu: Number(receiveMtu),
      transmitMtu: Number(transmitMtu),
      attributes: this.decodeAttributes(unescapeWire(attributes)),
    });
    const id = this.nextHandle++;
    this.listeners.set(id, listener);
    return ok(
      id,
      this.encodeEndpoint(listener.endpoint),
      listener.psm ?? this.virtualPsm(serviceUuid),
    );
  }

  private async accept(listenerId: number): Promise<string> {
    const listener = this.listeners.get(listenerId);
    if (!listener) throw new Error('Bluetooth listener is closed');
    return this.registerChannel(await listener.accept());
  }

  private async connect(payload: string): Promise<string> {
    const [address, psm, endpointValue, receiveMtu, transmitMtu] =
      payload.split('\t');
    const peerId = this.addressToPeer.get(address);
    if (!peerId) throw new Error('Unknown Bluetooth peer');
    const endpoint = endpointValue
      ? this.decodeEndpoint(unescapeWire(endpointValue))
      : this.findEndpoint(peerId, Number.parseInt(psm, 16));
    if (!endpoint) throw new Error('Bluetooth service endpoint is unavailable');
    return this.registerChannel(
      await this.host!.connect(endpoint, {
        receiveMtu: Number(receiveMtu),
        transmitMtu: Number(transmitMtu),
      }),
    );
  }

  private async send(payload: string): Promise<string> {
    const separator = payload.indexOf('\t');
    const state = this.requireChannel(Number(payload.slice(0, separator)));
    await state.channel.send(this.hexToBytes(payload.slice(separator + 1)));
    return ok();
  }

  private ready(channelId: number): string {
    return ok(this.requireChannel(channelId).packets.length > 0 ? 1 : 0);
  }

  private async receive(channelId: number): Promise<string> {
    const state = this.requireChannel(channelId);
    const packet =
      state.packets.shift() ??
      (await new Promise<Uint8Array>((resolve) => {
        state.waiters.push(resolve);
      }));
    return ok(this.bytesToHex(packet));
  }

  private async closeChannel(channelId: number): Promise<string> {
    const state = this.channels.get(channelId);
    if (state) {
      this.channels.delete(channelId);
      state.unsubscribe();
      for (const waiter of state.waiters.splice(0)) waiter(new Uint8Array());
      await state.channel.close();
    }
    return ok();
  }

  private async closeListener(listenerId: number): Promise<string> {
    const listener = this.listeners.get(listenerId);
    if (listener) {
      this.listeners.delete(listenerId);
      await listener.close();
    }
    return ok();
  }

  private registerChannel(channel: J2MEBluetoothPacketChannel): string {
    const id = this.nextHandle++;
    const state: ChannelState = {
      channel,
      packets: [],
      waiters: [],
      unsubscribe: () => {},
    };
    state.unsubscribe = channel.onPacket((packet) => {
      const waiter = state.waiters.shift();
      if (waiter) waiter(packet.slice());
      else state.packets.push(packet.slice());
    });
    this.channels.set(id, state);
    return ok(
      id,
      this.addressFor(channel.peerId),
      channel.receiveMtu,
      channel.transmitMtu,
    );
  }

  private requireChannel(id: number): ChannelState {
    const state = this.channels.get(id);
    if (!state) throw new Error('Bluetooth channel is closed');
    return state;
  }

  private encodeService(service: J2MEBluetoothDiscoveredService): string {
    const address = this.addressFor(service.peerId);
    const psm = service.psm ?? this.virtualPsm(service.serviceUuid);
    this.discoveredEndpoints.set(`${service.peerId}:${psm}`, service.endpoint);
    return [
      address,
      escapeWire(service.peerName ?? ''),
      service.deviceClass ?? 0,
      service.serviceUuid,
      escapeWire(this.encodeEndpoint(service.endpoint)),
      psm,
      escapeWire(this.encodeAttributes(service.attributes)),
    ].join('\t');
  }

  private encodeAttributes(
    attributes: J2MEBluetoothServiceAttribute[] | undefined,
  ): string {
    return (attributes ?? [])
      .map(({ id, type, value }) => `${id}=${type}=${escapeWire(value)}`)
      .join(';');
  }

  private decodeAttributes(value: string): J2MEBluetoothServiceAttribute[] {
    if (!value) return [];
    return value.split(';').flatMap((entry) => {
      const first = entry.indexOf('=');
      const second = entry.indexOf('=', first + 1);
      if (first < 1 || second < 0) return [];
      const id = Number(entry.slice(0, first));
      const type = Number(entry.slice(first + 1, second));
      if (!Number.isInteger(id) || !Number.isInteger(type)) return [];
      return [{ id, type, value: unescapeWire(entry.slice(second + 1)) }];
    });
  }

  private addressFor(peerId: string): string {
    const existing = this.peerToAddress.get(peerId);
    if (existing) return existing;
    let hash = 0xcbf29ce4;
    for (let i = 0; i < peerId.length; i++)
      hash = Math.imul(hash ^ peerId.charCodeAt(i), 0x01000193);
    let address =
      `${(hash >>> 0).toString(16).padStart(8, '0')}0000`.toUpperCase();
    let suffix = 0;
    while (
      this.addressToPeer.has(address) &&
      this.addressToPeer.get(address) !== peerId
    ) {
      address =
        `${(hash >>> 0).toString(16).padStart(8, '0')}${(++suffix).toString(16).padStart(4, '0')}`.toUpperCase();
    }
    this.peerToAddress.set(peerId, address);
    this.addressToPeer.set(address, peerId);
    return address;
  }

  private readonly discoveredEndpoints = new Map<
    string,
    J2MEBluetoothServiceEndpoint
  >();
  private findEndpoint(
    peerId: string,
    psm: number,
  ): J2MEBluetoothServiceEndpoint | undefined {
    return this.discoveredEndpoints.get(`${peerId}:${psm}`);
  }
  private encodeEndpoint(endpoint: J2MEBluetoothServiceEndpoint): string {
    return `${endpoint.transport}:${endpoint.token}`;
  }
  private decodeEndpoint(value: string): J2MEBluetoothServiceEndpoint {
    const separator = value.indexOf(':');
    return {
      transport: unescapeWire(value.slice(0, separator)),
      token: unescapeWire(value.slice(separator + 1)),
    };
  }
  private virtualPsm(uuid: string): number {
    let hash = 0;
    for (let i = 0; i < uuid.length; i++)
      hash = Math.imul(hash, 31) + uuid.charCodeAt(i);
    return 0x1001 | ((hash >>> 0) & 0xeeee);
  }
  private bytesToHex(bytes: Uint8Array): string {
    return Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join(
      '',
    );
  }
  private hexToBytes(value: string): Uint8Array {
    const bytes = new Uint8Array(value.length / 2);
    for (let i = 0; i < bytes.length; i++)
      bytes[i] = Number.parseInt(value.slice(i * 2, i * 2 + 2), 16);
    return bytes;
  }
}
