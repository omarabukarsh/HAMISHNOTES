import Foundation
import Security
import CryptoKit

/// رمز قفل المستندات: يُحفظ في سلسلة المفاتيح (Keychain) بصمةً مملّحة مكررة، لا في الإعدادات.
/// إن تعذّر الحفظ هناك تُحفظ البصمة نفسها في الإعدادات حتى لا يتعطل القفل.
enum PinStore {
    private static let service = "com.omarabukarsh.hamish.lock"
    private static let account = "pin"
    private static let fallbackKey = "lock_pin_v2"
    private static let rounds = 20_000

    private static func digest(_ pin: String, salt: Data) -> Data {
        var h = Data(SHA256.hash(data: salt + Data(pin.utf8)))
        for _ in 0..<rounds { h = Data(SHA256.hash(data: h + salt)) }
        return h
    }

    /// الملح (16 بايت) ثم البصمة.
    static func save(_ pin: String) {
        var salt = Data(count: 16)
        let ok = salt.withUnsafeMutableBytes { SecRandomCopyBytes(kSecRandomDefault, 16, $0.baseAddress!) } == errSecSuccess
        if !ok { salt = Data(UUID().uuidString.utf8.prefix(16)) }
        let value = salt + digest(pin, salt: salt)
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                    kSecAttrService as String: service, kSecAttrAccount as String: account]
        SecItemDelete(query as CFDictionary)
        var add = query
        add[kSecValueData as String] = value
        add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        if SecItemAdd(add as CFDictionary, nil) == errSecSuccess {
            UserDefaults.standard.removeObject(forKey: fallbackKey)
        } else {
            UserDefaults.standard.set(value, forKey: fallbackKey)
        }
    }

    static func load() -> Data? {
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                    kSecAttrService as String: service, kSecAttrAccount as String: account,
                                    kSecReturnData as String: true, kSecMatchLimit as String: kSecMatchLimitOne]
        var out: CFTypeRef?
        if SecItemCopyMatching(query as CFDictionary, &out) == errSecSuccess, let d = out as? Data, d.count > 16 { return d }
        if let d = UserDefaults.standard.data(forKey: fallbackKey), d.count > 16 { return d }
        return nil
    }

    static func matches(_ pin: String, _ stored: Data) -> Bool {
        let salt = Data(stored.prefix(16))
        return digest(pin, salt: salt) == Data(stored.dropFirst(16))
    }
}
