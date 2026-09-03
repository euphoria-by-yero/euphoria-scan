const express = require('express');
const app = express();
const admin = require('firebase-admin');
require('dotenv').config();

// 👉 Middleware
app.use(express.json());

// 🔐 TOKEN DE SEGURIDAD (solo tu APK podrá usarlo)
const TOKEN = "EUPHORIA-ADMIN-2026";

// 👉 Cargar credenciales Firebase
let serviceAccount;

if (process.env.FIREBASE_KEY) {
  // 🌐 PRODUCCIÓN (Render)
  serviceAccount = JSON.parse(process.env.FIREBASE_KEY);
  console.log("🔥 Firebase desde RENDER");
} else {
  // 💻 LOCAL
  serviceAccount = require('../firebase-key.json');
  console.log("🔥 Firebase desde LOCAL");
}

// 👉 Inicializar Firebase
admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();


// ==========================================
// 🎟️ ENDPOINT VALIDAR BOLETA
// ==========================================
app.post('/validar', async (req, res) => {
  try {
    // 🔐 Validación de seguridad
    const token = req.headers.authorization;

    if (token !== TOKEN) {
      return res.status(403).json({
        estado: 'no_autorizado'
      });
    }

    const { codigo } = req.body;

    if (!codigo) {
      return res.status(400).json({
        estado: 'invalido'
      });
    }

    // 🔍 Buscar boleta
    const ref = db.collection('boletas').doc(codigo);
    const doc = await ref.get();

    // ❌ No existe
    if (!doc.exists) {
      return res.json({
        estado: 'invalido'
      });
    }

    const data = doc.data();

    // 🔴 Ya usada
    if (data.usado) {
      return res.json({
        estado: 'usado',
        nombre: data.nombre || "Sin nombre"
      });
    }

    // 🟢 Marcar como usada
    await ref.update({
      usado: true,
      horaIngreso: new Date()
    });

    return res.json({
      estado: 'valido',
      nombre: data.nombre || "Sin nombre"
    });

  } catch (error) {
    console.error("❌ Error en /validar:", error);

    return res.status(500).json({
      estado: 'error'
    });
  }
});


// ==========================================
// ❤️ ENDPOINT DE PRUEBA
// ==========================================
app.get('/', (req, res) => {
  res.send('🎟️ API EUPHORIA VALIDADOR ACTIVA');
});


// ==========================================
// 🚀 SERVIDOR
// ==========================================
const PORT = process.env.PORT || 3000;

app.listen(PORT, () => {
  console.log(`🚀 Servidor corriendo en puerto ${PORT}`);
});