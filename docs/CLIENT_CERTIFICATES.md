# Client certificates

HomeCar can use an Android-installed client certificate to reach a Home Assistant server protected by mutual TLS (mTLS). Home Assistant still requires its own login. The certificate is an additional gate at the HTTPS endpoint.

## Phone setup

1. Install the certificate bundle supplied by the server administrator through Android's certificate installation settings. Choose the **VPN and apps** credential type if Android offers a choice. Keep its password and private key private.
2. Open HomeCar on the phone and open **HomeCar settings**.
3. Enter the full HTTPS server address, for example `https://home.example.com/`.
4. Choose the client certificate using Android's system picker. Review the selected alias and expiry date in HomeCar.
5. Save and open the dashboard, then sign in to Home Assistant.
6. Test once on home Wi-Fi and once on mobile data before connecting Android Auto. Complete setup while parked.

The selection grants **this app** access to the certificate. Selecting it in Chrome does not grant access to HomeCar. There is no need to copy the certificate into HomeCar or paste its password into the app.

## Security behavior

- Only the certificate alias is stored by HomeCar, scoped to the exact HTTPS hostname and port. Paths on the same origin share that choice. Another hostname or port needs its own selection.
- Android KeyChain performs private-key operations. HomeCar does not export or store the private key in its preferences, APK, source repository, or logs.
- Key and certificate access occurs off the UI thread. Expired, unavailable, or incompatible credentials are rejected.
- Changing or forgetting the selection clears WebView's client-certificate decision cache. Cancelling a replacement preserves the existing selection.
- **Forget** removes HomeCar's selection; the certificate remains installed in Android settings.
- Android Auto reuses the certificate granted on the phone. It does not open a certificate picker on the car screen.
- Server certificate validation remains enabled. A client certificate never permits bypassing an untrusted or expired server certificate.
- Local HTTP dashboards remain supported without client certificates. Use HTTPS for remote access.

The certificate controls access through possession of its private key. It does not uniquely attest the physical phone. Do not share or install the bundle on other devices if access should remain exclusive to one phone.

## Troubleshooting

**The certificate is missing from the picker.** Confirm it was installed for apps, includes a private key, is valid, and matches the server's accepted certificate types. Android manages which credentials an app can see.

**Access works in Chrome but is blocked in HomeCar.** Choose the certificate explicitly in HomeCar, then use Save & open or reload. Chrome and Android System WebView are separate clients with separate grants and connection state. A 403 response alone does not identify the reason: it can come from an access rule, a missing client certificate, or another server policy.

**A previously working certificate stops working.** Check its expiry, whether it was removed from Android, and whether the server revoked or replaced it. Select the replacement on the phone and retry.

**QUIC/HTTP/3.** Do not assume a Chrome flag also changes WebView. This app uses the supported Android WebView and KeyChain APIs; it does not use hidden Chromium flags, disable server validation, or proxy decrypted dashboard traffic through a custom client. Validate the installed WebView/provider version on the actual phone. Server-side protocol changes require their own review if a particular provider fails to negotiate client authentication.

## Device acceptance checks

These checks require the target phone and head unit; desktop unit tests cannot establish them:

| Check | Expected result |
| --- | --- |
| Home Wi-Fi | Dashboard loads and Home Assistant login works |
| Mobile data with selected certificate | Protected dashboard loads |
| Mobile data without an authorized certificate | Server denies access |
| Certificate picker cancelled | Existing selection remains usable |
| Certificate replaced/forgotten | Subsequent connection uses the new decision |
| Redirect to another hostname or port | Saved credential is not silently supplied there |
| App restart | Alias is retained and Android grant reused |
| Light/dark, large text, TalkBack | Settings remain readable and operable |
| Phone/car transition | Dashboard remains usable without a car-side picker |

Android Auto compatibility depends on the phone, Android Auto release, head unit, and installation method. The inherited projection architecture is unchanged.

## Platform references

- [Android KeyChain](https://developer.android.com/reference/android/security/KeyChain)
- [ClientCertRequest](https://developer.android.com/reference/android/webkit/ClientCertRequest)
- [WebView certificate decision cache](https://developer.android.com/reference/android/webkit/WebView#clearClientCertPreferences(java.lang.Runnable))
- [WebView server TLS error handling](https://developer.android.com/reference/android/webkit/WebViewClient#onReceivedSslError(android.webkit.WebView,%20android.webkit.SslErrorHandler,%20android.net.http.SslError))
