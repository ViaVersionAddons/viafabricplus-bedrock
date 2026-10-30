# ViaFabricPlus Bedrock

Addon for [ViaFabricPlus](https://github.com/ViaVersion/ViaFabricPlus) that adds Minecraft: Bedrock Edition support.

## Usage

1. Install [ViaFabricPlus](https://modrinth.com/mod/viafabricplus) 5.1.0 or newer and this addon.
2. Select the Bedrock version in the ViaFabricPlus screen.
3. Optionally, log in under `Settings > Bedrock > Account for Bedrock Edition`. This is needed for online mode servers,
   `Bedrock Realms` and `Bedrock Friends`.

## Addresses

| Address                                | Signaling                     |
|----------------------------------------|-------------------------------|
| `host[:port]`                          | HTTP, default port 19132      |
| `nethernet-lan://host[:port]`          | LAN, default port 7551        |
| `nethernet-xbox://network-id`          | Xbox WebSocket, needs account |
| `nethernet-xbox-json-rpc://network-id` | Xbox JSON-RPC, needs account  |

## Gradle

```kotlin
repositories {
    maven("https://repo.viaversion.com")
}

dependencies {
    runtimeOnly("com.viaversion:viafabricplus-bedrock:x.x.x") // Replace with the latest release
}
```

## Contact

- Issues: https://github.com/florianreuth/viafabricplus-bedrock/issues
- Discord: https://florianreuth.de/discord
