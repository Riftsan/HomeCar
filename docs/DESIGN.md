# Interface design

The phone setup uses Google's native Material Components for Android with a scoped Material 3 day/night theme. A scrollable settings sheet contains the server address, certificate state and expiry, and a clear Save & open action. The Home Assistant dashboard remains server-rendered in the existing WebView.

The implementation retains the Java/XML architecture. Material Components for Views is in maintenance; a full Compose migration would be a separate architectural change. The scoped theme avoids restyling inherited media and projection screens unintentionally.

## Design decisions

- Tonal surfaces, restrained teal accents, rounded cards, and semantic text/icon states in both themes.
- Native outlined text field, Material buttons, and Android's own certificate picker.
- Unconfigured phone installations open native setup with an empty address field, without requesting a placeholder website. Configured dashboard sessions retain the existing lifecycle.
- Phone controls have at least 48dp touch targets. Layouts scroll and use scalable text; status is conveyed in words as well as color.
- Certificate setup stays on the phone. The car retains minimal navigation and the existing dashboard/session behavior.
- A valid local certificate is described as ready for use, not proof that the server accepted the connection.
- No imitation certificate picker, embedded private key, custom authentication proxy, or TLS bypass.

## Consultation and skills

The requested [Lovable design consultation](https://lovable.dev/projects/30f1ad6d-a477-42fa-928b-d48ec2bf80fa) produced a clickable Material 3 reference and an Android design handoff. The prototype uses fictional data. It illustrates a broader future connection-status screen and parked/driving states; those mock states are not evidence of implemented vehicle-state detection or a tested Android build.

The following community skills were discovered through skills.sh and reviewed at commit `c5bf6731b8441019418784484cca1578413e6ad3` of `krutikjain/android-agent-skills`:

- [Android Material 3 design system](https://skills.sh/krutikjain/android-agent-skills/android-material3-design-system): native components, theme roles, touch targets and accessibility.
- [Android View system foundations](https://skills.sh/krutikjain/android-agent-skills/android-viewsystem-foundations): lifecycle ownership, insets, and incremental integration with existing Views.
- [Android security best practices](https://skills.sh/krutikjain/android-agent-skills/android-security-best-practices): platform KeyChain, private signing material, TLS validation and minimal credential persistence.

These community references inform implementation choices; official Android documentation defines the platform behavior. No global skill installation or community setup scripts are required to build or use HomeCar.

[Google Material Components documentation](https://github.com/material-components/material-components-android/blob/master/docs/getting-started.md) describes the native library and its maintenance status.
