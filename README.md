# 🔐 End-to-End Encrypted Chat Application

A secure real-time messaging Android application that ensures complete privacy using hybrid encryption (RSA + AES).

---

## 🚀 Overview

This application enables users to communicate securely with end-to-end encryption, ensuring that messages can only be read by the sender and the receiver — not even the server.

The system combines:

- 📱 Android frontend (Kotlin)
- 🔐 Hybrid Encryption (RSA + AES)
- ☁️ Firebase Backend (Realtime Database + Storage)

---

## ⚡ Features

- 🔒 End-to-End Encrypted Messaging (RSA + AES)
- ⚡ Real-time chat using Firebase Realtime Database
- 📁 Secure media sharing (images/files)
- 👤 User authentication with Firebase Auth
- 📡 Low-latency message delivery
- 🧵 Story/status feature (like WhatsApp)

---

## 🔐 How Encryption Works

1. **Key Generation**
   - Each user generates a public-private key pair (RSA)

2. **Message Encryption**
   - A random AES key is generated for each message
   - Message is encrypted using AES

3. **Key Encryption**
   - AES key is encrypted using receiver’s RSA public key

4. **Transmission**
   - Encrypted message + encrypted AES key sent to Firebase

5. **Decryption**
   - Receiver decrypts AES key using private RSA key
   - Uses AES key to decrypt the message

---

## 🛠️ Tech Stack

### 📱 Android
- Kotlin
- Jetpack Compose / XML
- MVVM Architecture

### 🔐 Security
- RSA (asymmetric encryption)
- AES (symmetric encryption)

### ☁️ Backend
- Firebase Authentication
- Firebase Realtime Database
- Firebase Cloud Storage

---

## 📸 Screenshots

<p align="center">
  <img src="https://github.com/user-attachments/assets/c4b37945-264c-418f-b8d8-58daaa4fd474" width="18%" />
  <img src="https://github.com/user-attachments/assets/7a684aba-b1a1-4941-bb1d-f97a3fbe06d9" width="18%" />
  <img src="https://github.com/user-attachments/assets/8c670629-0d2d-4bbe-8352-48a96ec30a92" width="18%" />
  <img src="https://github.com/user-attachments/assets/10f0836a-c8af-48ed-be5d-b45d18358019" width="18%" />
  <img src="https://github.com/user-attachments/assets/88d893ec-e7cb-47fc-86f6-782d1fc1b12d" width="18%" />
</p>
---

## 🔑 Key Highlights

- Messages are never stored in plain text
- Encryption & decryption handled entirely on client-side
- Backend only stores encrypted payloads
- Ensures true end-to-end privacy

---

## 🚧 Challenges Faced

- Implementing hybrid encryption efficiently on mobile
- Managing secure key exchange
- Handling encrypted media transfer
- Maintaining real-time performance with encryption overhead

---

## 📈 Future Improvements

- Group chat encryption
- Forward secrecy (per-session keys)
- Key rotation mechanism
- Encrypted push notifications
- Voice/video call encryption
