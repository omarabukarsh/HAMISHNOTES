import Foundation
import AuthenticationServices
import CryptoKit
import UIKit

/// تسجيل الدخول بحساب Google (نفس GoogleAuth.kt في أندرويد): صفحة Google الآمنة من النظام
/// (ASWebAuthenticationSession) مع PKCE، بلا مكتبات خارجية. يعمل حتى بحساب Apple المجاني.
/// معرّف عميل iOS يأتي من سر GOOGLE_IOS_CLIENT_ID عند البناء (مفتاح GoogleClientID في Info.plist).
final class GoogleAuth: NSObject, ASWebAuthenticationPresentationContextProviding {
    static let shared = GoogleAuth()
    private var session: ASWebAuthenticationSession?

    enum Failure: Error { case notConfigured, cancelled, failed }

    var clientId: String {
        (Bundle.main.object(forInfoDictionaryKey: "GoogleClientID") as? String ?? "").trimmingCharacters(in: .whitespaces)
    }
    var configured: Bool { clientId.hasSuffix(".apps.googleusercontent.com") }

    /// المخطط العكسي لمعرّف العميل (يقبله Google رابطَ عودةٍ لعملاء iOS).
    private var scheme: String {
        "com.googleusercontent.apps." + clientId.replacingOccurrences(of: ".apps.googleusercontent.com", with: "")
    }

    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        UIApplication.shared.connectedScenes.compactMap { ($0 as? UIWindowScene)?.keyWindow }.first ?? ASPresentationAnchor()
    }

    private static func b64url(_ d: Data) -> String {
        d.base64EncodedString().replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "")
    }

    /// يعيد (البريد، الاسم).
    @MainActor
    func signIn() async -> Result<(String, String?), Failure> {
        guard configured else { return .failure(.notConfigured) }
        var bytes = [UInt8](repeating: 0, count: 32)
        _ = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        let verifier = Self.b64url(Data(bytes))
        let challenge = Self.b64url(Data(SHA256.hash(data: Data(verifier.utf8))))
        let redirect = scheme + ":/oauth2redirect"
        var c = URLComponents(string: "https://accounts.google.com/o/oauth2/v2/auth")!
        c.queryItems = [
            URLQueryItem(name: "client_id", value: clientId),
            URLQueryItem(name: "redirect_uri", value: redirect),
            URLQueryItem(name: "response_type", value: "code"),
            URLQueryItem(name: "scope", value: "openid email profile"),
            URLQueryItem(name: "code_challenge", value: challenge),
            URLQueryItem(name: "code_challenge_method", value: "S256"),
            URLQueryItem(name: "prompt", value: "select_account")
        ]
        guard let authURL = c.url else { return .failure(.failed) }
        let callback: URL? = await withCheckedContinuation { cont in
            let s = ASWebAuthenticationSession(url: authURL, callbackURLScheme: scheme) { url, _ in cont.resume(returning: url) }
            s.presentationContextProvider = self
            s.prefersEphemeralWebBrowserSession = false
            session = s
            if !s.start() { cont.resume(returning: nil) }
        }
        session = nil
        guard let cb = callback, let code = URLComponents(url: cb, resolvingAgainstBaseURL: false)?.queryItems?.first(where: { $0.name == "code" })?.value else {
            return .failure(.cancelled)
        }
        // استبدال الرمز بهوية المستخدم
        var req = URLRequest(url: URL(string: "https://oauth2.googleapis.com/token")!)
        req.httpMethod = "POST"
        req.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        let form = ["code": code, "client_id": clientId, "redirect_uri": redirect, "code_verifier": verifier, "grant_type": "authorization_code"]
        req.httpBody = form.map { "\($0.key)=\($0.value.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? $0.value)" }.joined(separator: "&").data(using: .utf8)
        guard let (data, _) = try? await URLSession.shared.data(for: req),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let idToken = json["id_token"] as? String else { return .failure(.failed) }
        // بيانات الحساب من رمز الهوية (JWT)
        let parts = idToken.split(separator: ".")
        guard parts.count >= 2 else { return .failure(.failed) }
        var p = String(parts[1]).replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        while p.count % 4 != 0 { p += "=" }
        guard let pd = Data(base64Encoded: p), let claims = try? JSONSerialization.jsonObject(with: pd) as? [String: Any],
              let email = claims["email"] as? String else { return .failure(.failed) }
        UserDefaults.standard.set("google", forKey: "account_provider")
        return .success((email, claims["name"] as? String))
    }

    static func message(_ f: Failure) -> String {
        switch f {
        case .notConfigured: return L("تسجيل الدخول بـ Google غير مُعدّ بعد في هذه النسخة", "Google sign-in is not set up in this build yet")
        case .cancelled: return L("أُلغي تسجيل الدخول", "Sign-in cancelled")
        case .failed: return L("تعذّر تسجيل الدخول، حاول مرة أخرى", "Sign-in failed, please try again")
        }
    }
}
