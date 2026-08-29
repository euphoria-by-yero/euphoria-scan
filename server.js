const express = require('express');
const app = express();
const path = require('path');

app.use(express.json());

// 🔥 FIREBASE
const admin = require('firebase-admin');

let serviceAccount;

if (process.env.FIREBASE_KEY) {
  serviceAccount = JSON.parse(process.env.FIREBASE_KEY);
} else {
  serviceAccount = require('./firebase-key.json');
}

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

// 👉 FRONTEND
app.use(express.static(path.join(__dirname, 'public')));

app.get('/', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

// 🎟️ VALIDAR BOLETAS
app.post('/validar-boleta', async (req, res) => {
  try {
    const { codigo } = req.body;

    const ref = db.collection('boletas').doc(codigo);
    const doc = await ref.get();

    if (!doc.exists) {
      return res.json({ estado: "invalido" });
    }

    const data = doc.data();

    if (data.usado) {
      return res.json({ estado: "usado" });
    }

    await ref.update({
      usado: true,
      hora_ingreso: new Date()
    });

    // 🔢 CONTADOR
    const contRef = db.collection('control').doc('ingresos');
    await contRef.set({
      total: admin.firestore.FieldValue.increment(1)
    }, { merge: true });

    const contDoc = await contRef.get();
    const total = contDoc.data().total;

    return res.json({ estado: "valido", total });

  } catch (error) {
    console.error(error);
    res.status(500).json({ error: 'Error' });
  }
});

// 🚀 SERVER
const PORT = 3000;
app.listen(PORT, () => {
  console.log(`Scanner listo en puerto ${PORT}`);
});