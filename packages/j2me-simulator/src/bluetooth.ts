export interface J2MEBluetoothCapabilities {
  peerId: string;
  name: string;
  deviceClass?: number;
}

export interface J2MEBluetoothServiceEndpoint {
  transport: string;
  token: string;
}

export interface J2MEBluetoothServiceAttribute {
  id: number;
  type: number;
  value: string;
}

export interface J2MEBluetoothDiscoveredService {
  peerId: string;
  peerName?: string;
  deviceClass?: number;
  serviceUuid: string;
  endpoint: J2MEBluetoothServiceEndpoint;
  psm?: number;
  attributes?: J2MEBluetoothServiceAttribute[];
}

export interface J2MEBluetoothDiscoveryQuery {
  peerId?: string;
  serviceUuids?: string[];
}

export interface J2MEBluetoothServiceDefinition {
  serviceUuid: string;
  serviceName?: string;
  receiveMtu: number;
  transmitMtu: number;
  attributes?: J2MEBluetoothServiceAttribute[];
}

export interface J2MEBluetoothChannelOptions {
  receiveMtu: number;
  transmitMtu: number;
}

export interface J2MEBluetoothPacketChannel {
  readonly peerId: string;
  readonly receiveMtu: number;
  readonly transmitMtu: number;
  send(packet: Uint8Array): void | Promise<void>;
  onPacket(listener: (packet: Uint8Array) => void): () => void;
  close(): void | Promise<void>;
}

export interface J2MEBluetoothServiceListener {
  readonly endpoint: J2MEBluetoothServiceEndpoint;
  readonly psm?: number;
  accept(): Promise<J2MEBluetoothPacketChannel>;
  close(): void | Promise<void>;
}

export interface J2MEBluetoothHost {
  getCapabilities():
    | J2MEBluetoothCapabilities
    | Promise<J2MEBluetoothCapabilities>;
  discoverServices(
    query: J2MEBluetoothDiscoveryQuery,
  ):
    | J2MEBluetoothDiscoveredService[]
    | Promise<J2MEBluetoothDiscoveredService[]>;
  publishService(
    definition: J2MEBluetoothServiceDefinition,
  ): Promise<J2MEBluetoothServiceListener>;
  connect(
    endpoint: J2MEBluetoothServiceEndpoint,
    options: J2MEBluetoothChannelOptions,
  ): Promise<J2MEBluetoothPacketChannel>;
}
