package com.example.euphoriascanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

import com.example.euphoriascanner.ui.theme.EuphoriaScannerTheme

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener

import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class MainActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth

    companion object {
        private const val DATABASE_URL =
            "https://euphoriascanner-default-rtdb.firebaseio.com"
    }

    // =============================================================
    // REFERENCIA CENTRAL DE FIREBASE
    // =============================================================

    private fun obtenerBaseDatos(): FirebaseDatabase {
        return FirebaseDatabase.getInstance(DATABASE_URL)
    }


    // =============================================================
    // INICIO DE LA APLICACIÓN
    // =============================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        enableEdgeToEdge()

        setContent {

            EuphoriaScannerTheme {

                var usuarioAutenticado by remember {
                    mutableStateOf(auth.currentUser != null)
                }

                if (usuarioAutenticado) {

                    PantallaEscaner(
                        onCerrarSesion = {
                            auth.signOut()
                            usuarioAutenticado = false
                        }
                    )

                } else {

                    PantallaLogin(
                        onLoginExitoso = {
                            usuarioAutenticado = true
                        }
                    )
                }
            }
        }
    }


    // =============================================================
    // PANTALLA DE INICIO DE SESIÓN
    // =============================================================

    @Composable
    private fun PantallaLogin(
        onLoginExitoso: () -> Unit
    ) {

        var correo by remember {
            mutableStateOf("yeropuntosmartmocoa@gmail.com")
        }

        var contrasena by remember {
            mutableStateOf("")
        }

        var mensaje by remember {
            mutableStateOf("")
        }

        var cargando by remember {
            mutableStateOf(false)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),

            verticalArrangement = Arrangement.Center,

            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = "EUPHORIA SCANNER",
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = correo,
                onValueChange = { correo = it },
                label = { Text("Correo electrónico") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = contrasena,
                onValueChange = { contrasena = it },
                label = { Text("Contraseña") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {

                    if (correo.isBlank() || contrasena.isBlank()) {

                        mensaje = "Introduce el correo y la contraseña."

                        return@Button
                    }

                    cargando = true
                    mensaje = ""

                    auth.signInWithEmailAndPassword(
                        correo.trim(),
                        contrasena
                    ).addOnCompleteListener { tarea ->

                        cargando = false

                        if (tarea.isSuccessful) {

                            contrasena = ""

                            onLoginExitoso()

                        } else {

                            mensaje = tarea.exception?.localizedMessage
                                ?: "No se pudo iniciar sesión."
                        }
                    }
                },

                enabled = !cargando,

                modifier = Modifier.fillMaxWidth()

            ) {

                Text(
                    if (cargando) "Iniciando sesión..."
                    else "Iniciar sesión"
                )
            }

            if (cargando) {

                Spacer(modifier = Modifier.height(16.dp))

                CircularProgressIndicator()
            }

            if (mensaje.isNotBlank()) {

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = mensaje,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }


    // =============================================================
    // PANTALLA PRINCIPAL DEL ESCÁNER
    // =============================================================

    @Composable
    private fun PantallaEscaner(
        onCerrarSesion: () -> Unit
    ) {

        var scanResult by remember {
            mutableStateOf("Escanea un QR")
        }

        var totalBoletas by remember {
            mutableStateOf(0L)
        }

        var totalEscaneados by remember {
            mutableStateOf(0L)
        }

        var ingresosAutorizados by remember {
            mutableStateOf(0L)
        }

        var cargandoBoletas by remember {
            mutableStateOf(true)
        }

        var errorBoletas by remember {
            mutableStateOf("")
        }

        // =========================================================
        // REFERENCIAS A FIREBASE
        // =========================================================

        val baseDatos = obtenerBaseDatos()

        val boletasRef = baseDatos
            .getReference("boletas")

        val estadisticasRef = baseDatos
            .getReference("estadisticas")

        val escaneadosRef = estadisticasRef
            .child("totalEscaneados")

        val autorizadosRef = estadisticasRef
            .child("totalAutorizados")


        // =========================================================
        // ACTUALIZACIÓN AUTOMÁTICA DE LOS CONTADORES
        // =========================================================

        DisposableEffect(Unit) {

            // LISTENER TOTAL DE BOLETAS

            val listenerBoletas =
                object : ValueEventListener {

                    override fun onDataChange(snapshot: DataSnapshot) {

                        totalBoletas = snapshot.childrenCount

                        cargandoBoletas = false

                        errorBoletas = ""
                    }

                    override fun onCancelled(error: DatabaseError) {

                        cargandoBoletas = false

                        errorBoletas = error.message
                    }
                }


            // LISTENER ESCANEADOS

            val listenerEscaneados =
                object : ValueEventListener {

                    override fun onDataChange(snapshot: DataSnapshot) {

                        totalEscaneados = snapshot.value
                            ?.toString()
                            ?.toLongOrNull() ?: 0L
                    }

                    override fun onCancelled(error: DatabaseError) {

                        scanResult =
                            "Error al actualizar escaneos: ${error.message}"
                    }
                }


            // LISTENER INGRESOS AUTORIZADOS

            val listenerAutorizados =
                object : ValueEventListener {

                    override fun onDataChange(snapshot: DataSnapshot) {

                        ingresosAutorizados = snapshot.value
                            ?.toString()
                            ?.toLongOrNull() ?: 0L
                    }

                    override fun onCancelled(error: DatabaseError) {

                        scanResult =
                            "Error al actualizar ingresos: ${error.message}"
                    }
                }


            // ACTIVAR LISTENERS

            boletasRef.addValueEventListener(listenerBoletas)

            escaneadosRef.addValueEventListener(listenerEscaneados)

            autorizadosRef.addValueEventListener(listenerAutorizados)


            // ELIMINAR LISTENERS AL CERRAR LA PANTALLA

            onDispose {

                boletasRef.removeEventListener(listenerBoletas)

                escaneadosRef.removeEventListener(listenerEscaneados)

                autorizadosRef.removeEventListener(listenerAutorizados)
            }
        }


        // =========================================================
        // CÁLCULO DE BOLETAS PENDIENTES
        // =========================================================

        val boletasPendientes =
            (totalBoletas - ingresosAutorizados).coerceAtLeast(0L)


        // =========================================================
        // LANZADOR DEL ESCÁNER QR
        // =========================================================

        val launcher = rememberLauncherForActivityResult(
            contract = ScanContract()

        ) { result ->

            val texto = result.contents

            if (texto == null) {

                scanResult = "Escaneo cancelado"

            } else {

                registrarEscaneo()

                procesarQR(texto) { mensaje ->

                    scanResult = mensaje
                }
            }
        }


        // =========================================================
        // INTERFAZ PRINCIPAL
        // =========================================================

        Column(

            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),

            verticalArrangement = Arrangement.Center,

            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = "EUPHORIA SCANNER",
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(24.dp))


            // =====================================================
            // RESULTADO DEL ESCANEO
            // =====================================================

            Text(
                text = scanResult,
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(24.dp))


            // =====================================================
            // TOTAL DE BOLETAS REGISTRADAS
            // =====================================================

            Text(
                text = "BOLETAS REGISTRADAS",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = if (cargandoBoletas) {
                    "Consultando Firebase..."
                } else if (errorBoletas.isNotBlank()) {
                    "Error: $errorBoletas"
                } else {
                    totalBoletas.toString()
                },
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(18.dp))


            // =====================================================
            // TOTAL ESCANEADOS
            // =====================================================

            Text(
                text = "Códigos escaneados: $totalEscaneados",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(12.dp))


            // =====================================================
            // INGRESOS AUTORIZADOS
            // =====================================================

            Text(
                text = "Ingresos autorizados: $ingresosAutorizados",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(12.dp))


            // =====================================================
            // BOLETAS PENDIENTES
            // =====================================================

            Text(
                text = "Boletas pendientes: $boletasPendientes",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(24.dp))


            // =====================================================
            // PORCENTAJE DE INGRESO
            // =====================================================

            val porcentajeIngreso = if (totalBoletas > 0L) {

                (ingresosAutorizados * 100.0 / totalBoletas)

            } else {

                0.0
            }

            Text(
                text = "Porcentaje de ingreso: ${
                    String.format(Locale.getDefault(), "%.1f", porcentajeIngreso)
                }%",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(30.dp))


            // =====================================================
            // BOTÓN ESCANEAR
            // =====================================================

            Button(
                onClick = {

                    val options = ScanOptions().apply {

                        setPrompt("Escanea un código QR")

                        setBeepEnabled(true)

                        setOrientationLocked(false)
                    }

                    launcher.launch(options)
                }

            ) {

                Text("ESCANEAR QR")
            }


            Spacer(modifier = Modifier.height(12.dp))


            // =====================================================
            // BOTÓN ACTUALIZAR DATOS
            // =====================================================

            Button(

                onClick = {

                    cargandoBoletas = true

                    errorBoletas = ""

                    boletasRef.get()

                        .addOnSuccessListener { snapshot ->

                            totalBoletas = snapshot.childrenCount

                            cargandoBoletas = false
                        }

                        .addOnFailureListener { error ->

                            cargandoBoletas = false

                            errorBoletas = error.message
                                ?: "No se pudieron actualizar las boletas."
                        }

                }

            ) {

                Text("ACTUALIZAR DATOS")
            }


            Spacer(modifier = Modifier.height(12.dp))


            // =====================================================
            // CERRAR SESIÓN
            // =====================================================

            Button(
                onClick = onCerrarSesion
            ) {

                Text("Cerrar sesión")
            }
        }
    }


    // =============================================================
    // PROCESAR QR
    // =============================================================

    private fun procesarQR(
        texto: String,
        onResultado: (String) -> Unit
    ) {

        val lineas = texto
            .trim()
            .lines()
            .filter { it.isNotBlank() }


        // =========================================================
        // VERIFICAR ESTRUCTURA DEL QR
        // =========================================================

        if (lineas.size != 2) {

            onResultado(
                "DATOS NO VÁLIDOS\n\n" +
                        "El código QR no tiene el formato requerido."
            )

            return
        }


        val boleta = lineas[0].trim()

        val cedulaLinea = lineas[1].trim()


        // =========================================================
        // VERIFICAR FORMATO DE CÉDULA
        // =========================================================

        if (!cedulaLinea.lowercase(Locale.ROOT).startsWith("cc.")) {

            onResultado(
                "DATOS NO VÁLIDOS\n\n" +
                        "Formato de cédula incorrecto."
            )

            return
        }


        val cedula = cedulaLinea.substring(3).trim()


        // =========================================================
        // VALIDAR INFORMACIÓN
        // =========================================================

        if (
            boleta.isBlank() ||
            cedula.isBlank() ||
            !cedula.all { it.isDigit() }
        ) {

            onResultado(
                "DATOS NO VÁLIDOS\n\n" +
                        "La información del código QR es incorrecta."
            )

            return
        }


        onResultado(
            "VALIDANDO...\n\nBoleta: $boleta"
        )


        validarIngreso(
            boleta,
            cedula,
            onResultado
        )
    }


    // =============================================================
    // VALIDAR INGRESO
    // =============================================================

    private fun validarIngreso(
        boleta: String,
        cedula: String,
        onResultado: (String) -> Unit
    ) {

        val ref = obtenerBaseDatos()
            .getReference("boletas")
            .child(boleta)


        // =========================================================
        // CONSULTAR BOLETA EN FIREBASE
        // =========================================================

        ref.get()

            .addOnSuccessListener { snapshot ->


                // =================================================
                // 1. BOLETA INEXISTENTE
                // =================================================

                if (!snapshot.exists()) {

                    onResultado(
                        "DATOS NO EXISTEN\n\n" +
                                "Esta boleta no está registrada en Firebase."
                    )

                    return@addOnSuccessListener
                }


                // =================================================
                // 2. COMPROBAR SI YA FUE UTILIZADA
                // =================================================

                val usada = snapshot
                    .child("usada")
                    .getValue(Boolean::class.java) ?: false

                if (usada) {

                    onResultado(
                        "BOLETA YA UTILIZADA\n\n" +
                                "Esta entrada ya fue registrada."
                    )

                    return@addOnSuccessListener
                }


                // =================================================
                // 3. COMPROBAR CÉDULA
                // =================================================

                val cedulaDB = snapshot
                    .child("cedula")
                    .value
                    ?.toString()
                    ?.trim()

                if (cedulaDB != cedula) {

                    onResultado(
                        "DATOS NO COINCIDEN\n\n" +
                                "La cédula no corresponde a esta boleta."
                    )

                    return@addOnSuccessListener
                }


                // =================================================
                // 4. FECHA Y HORA
                // =================================================

                val ahora = Date()

                val fecha = SimpleDateFormat(
                    "yyyy-MM-dd",
                    Locale.getDefault()
                ).format(ahora)

                val hora = SimpleDateFormat(
                    "HH:mm:ss",
                    Locale.getDefault()
                ).format(ahora)

                val timestamp = System.currentTimeMillis()


                // =================================================
                // 5. TRANSACCIÓN PARA EVITAR DUPLICADOS
                // =================================================

                ref.runTransaction(object : Transaction.Handler {

                    override fun doTransaction(
                        currentData: MutableData
                    ): Transaction.Result {

                        if (currentData.value == null) {

                            return Transaction.abort()
                        }

                        val cedulaActual = currentData
                            .child("cedula")
                            .value
                            ?.toString()
                            ?.trim()

                        val yaUsada = currentData
                            .child("usada")
                            .value as? Boolean ?: false

                        if (yaUsada || cedulaActual != cedula) {

                            return Transaction.abort()
                        }

                        currentData.child("usada").value = true

                        currentData.child("fechaIngreso").value = fecha

                        currentData.child("horaIngreso").value = hora

                        currentData.child("timestampIngreso").value = timestamp

                        return Transaction.success(currentData)
                    }


                    override fun onComplete(
                        error: DatabaseError?,
                        committed: Boolean,
                        currentData: DataSnapshot?
                    ) {

                        when {

                            error != null -> {

                                onResultado(
                                    "ERROR AL REGISTRAR EL INGRESO\n\n" +
                                            error.message
                                )
                            }


                            !committed -> {

                                val datosActuales = currentData

                                when {

                                    datosActuales == null ||
                                            !datosActuales.exists() -> {

                                        onResultado(
                                            "DATOS NO EXISTEN"
                                        )
                                    }

                                    datosActuales
                                        .child("usada")
                                        .getValue(Boolean::class.java) == true -> {

                                        onResultado(
                                            "BOLETA YA UTILIZADA\n\n" +
                                                    "Esta entrada ya fue registrada."
                                        )
                                    }

                                    datosActuales
                                        .child("cedula")
                                        .value
                                        ?.toString()
                                        ?.trim() != cedula -> {

                                        onResultado(
                                            "DATOS NO COINCIDEN\n\n" +
                                                    "La cédula no corresponde a esta boleta."
                                        )
                                    }

                                    else -> {

                                        onResultado(
                                            "NO SE PUDO AUTORIZAR EL INGRESO"
                                        )
                                    }
                                }
                            }


                            else -> {

                                onResultado(
                                    "INGRESO AUTORIZADO\n\n" +
                                            "Boleta: $boleta\n" +
                                            "Cédula: $cedula\n" +
                                            "Fecha: $fecha\n" +
                                            "Hora: $hora"
                                )

                                registrarIngresoAutorizado()
                            }
                        }
                    }
                })
            }


            .addOnFailureListener { error ->

                onResultado(
                    "ERROR DE CONEXIÓN CON FIREBASE\n\n" +
                            error.message
                )
            }
    }


    // =============================================================
    // REGISTRAR CÓDIGO ESCANEADO
    // =============================================================

    private fun registrarEscaneo() {

        val ref = obtenerBaseDatos()
            .getReference("estadisticas")
            .child("totalEscaneados")

        ref.runTransaction(object : Transaction.Handler {

            override fun doTransaction(
                currentData: MutableData
            ): Transaction.Result {

                val actual = currentData.value
                    ?.toString()
                    ?.toLongOrNull() ?: 0L

                currentData.value = actual + 1L

                return Transaction.success(currentData)
            }

            override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                currentData: DataSnapshot?
            ) {
                // El contador se actualiza automáticamente.
            }
        })
    }


    // =============================================================
    // REGISTRAR INGRESO AUTORIZADO
    // =============================================================

    private fun registrarIngresoAutorizado() {

        val ref = obtenerBaseDatos()
            .getReference("estadisticas")
            .child("totalAutorizados")

        ref.runTransaction(object : Transaction.Handler {

            override fun doTransaction(
                currentData: MutableData
            ): Transaction.Result {

                val actual = currentData.value
                    ?.toString()
                    ?.toLongOrNull() ?: 0L

                currentData.value = actual + 1L

                return Transaction.success(currentData)
            }

            override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                currentData: DataSnapshot?
            ) {
                // El contador se actualiza automáticamente.
            }
        })
    }
}