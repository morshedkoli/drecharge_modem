# 📶 dRecharge Modem Controller — Hardware GSM Modem Automation Daemon

[![Platform](https://img.shields.io/badge/Platform-Android_|_Java-3DDC84?style=for-the-badge&logo=android)](https://developer.android.com/)
[![Protocol](https://img.shields.io/badge/Protocol-AT_Commands_|_USSD-FF6F00?style=for-the-badge)](https://en.wikipedia.org/wiki/Hayes_command_set)
[![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)](LICENSE)

An automated hardware modem integration service and Android background daemon that interfaces directly with multi-SIM telecom modem pools. Dispatches airtime recharge USSD requests and parses confirmation SMS receipts in real-time.

---

## 🌟 Capabilities

- **⚡ Automated USSD Execution**: Executes direct operator USSD codes (e.g., Grameenphone, Banglalink, Robi) for instant top-ups.
- **📩 Real-time SMS Parsing**: Intercepts telecom transaction IDs, remaining balances, and confirmation messages.
- **🔄 Queue Worker**: Receives dispatched recharge tasks from central dRecharge payment servers over secure WebSockets / HTTP.

---

## 🚀 Build Instructions

```bash
git clone https://github.com/morshedkoli/drecharge_modem.git
cd drecharge_modem
./gradlew assembleDebug
```

---

## 📄 License

Licensed under the [MIT License](LICENSE).
