package com.example.beacon

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.RemoteException
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.altbeacon.beacon.BeaconConsumer
import org.altbeacon.beacon.BeaconManager
import org.altbeacon.beacon.BeaconParser
import org.altbeacon.beacon.Region
import com.example.beacon.data.entities.Position
import com.example.beacon.data.entities.BeaconPosition
import android.graphics.*
import android.view.MotionEvent
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast

@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity(), BeaconConsumer {

    private lateinit var beaconManager: BeaconManager
    private val beaconIdentifiers = mapOf(
        Pair(1234 to 4321, "A"),
        Pair(7621 to 42517, "B"),
        Pair(8358 to 30324, "C"),
        Pair(60390 to 23173, "D"),
        Pair(23835 to 50544, "E"),
        Pair(5895 to 10259, "F"),
        Pair(34308 to 7692, "G"),
        Pair(64608 to 31, "H")
    )

    private val beaconPositions = mapOf(
        "A" to BeaconPosition(14.0, 4.0, "A"), // Quarto 1
        "B" to BeaconPosition(10.0, 5.0, "B"), // Cozinha
        "C" to BeaconPosition(10.0, 6.0, "C"), // Sala de Jantar
        "D" to BeaconPosition(10.0, 11.0, "D"), // Sala de Estar
        "E" to BeaconPosition(13.0, 6.0, "E"), // Quarto 2
        "F" to BeaconPosition(14.0, 9.0, "F"), // Quarto 3
        "G" to BeaconPosition(11.0, 4.0, "G"), // Arrumos
        "H" to BeaconPosition(7.0, 3.0, "H")  // Garagem
    )

    private var currentLocationView: ImageView? = null
    private val rssiReadings = mutableMapOf<String, MutableList<Double>>()
    private val windowSize = 5 // Tamanho da janela para média móvel
    private var weights = IntArray(256) { 0 } // Pesos iniciais (16x16)
    private var size = 16
    private var currentEstimatedPosition: Pair<Int, Int>? = null
    private val originalGridSize = 16 // Define o tamanho da matriz original

    // Mapa para armazenar os ícones de peso
    private val weightIcons = mutableMapOf<Pair<Int, Int>, TextView>()
    private val pathViews = mutableListOf<View>() // Armazena as visualizações do caminho

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Verifica e solicita permissões
        requestPermissionsIfNeeded()

        // Configura o Spinner para selecionar o tamanho da matriz
        val matrixSizeSpinner = findViewById<Spinner>(R.id.matrix_size_spinner)
        val adapter = ArrayAdapter.createFromResource(
            this,
            R.array.matrix_sizes,
            android.R.layout.simple_spinner_item
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        matrixSizeSpinner.adapter = adapter
        matrixSizeSpinner.setSelection(3) // Seleciona o tamanho padrão 16x16

        matrixSizeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                size = when (position) {
                    0 -> 8
                    1 -> 16
                    2 -> 32
                    3 -> 64
                    else -> 16
                }
                weights = IntArray(size * size) { 0 }
                drawGridOnMap()
                currentEstimatedPosition?.let { updateCurrentLocationOnMap(it) }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Inicia o BeaconManager
        beaconManager = BeaconManager.getInstanceForApplication(this)
        beaconManager.beaconParsers.add(BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"))

        // Desenha a grelha na imagem da planta
        drawGridOnMap()

        // Inicializa o listener de toque no mapa
        val mapLayout = findViewById<RelativeLayout>(R.id.map_layout)
        mapLayout.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) { // Verifique apenas eventos de toque inicial
                val escala = 1000f / size // Ajustar conforme necessário para o tamanho do grid
                val touchedX = (event.x / escala).toInt()
                val touchedY = (event.y / escala).toInt()
                val index = touchedY * size + touchedX
                if (index in weights.indices) {
                    weights[index] = (weights[index] % 5) + 1 // Definir pesos sequenciais de 1 a 5
                    drawWeightOnGrid(touchedX, touchedY, weights[index], mapLayout)
                    Toast.makeText(this, "Peso ajustado para ${weights[index]} na posição ($touchedX, $touchedY)", Toast.LENGTH_SHORT).show()
                }
            }
            true
        }

        beaconManager.bind(this)
    }

    private fun requestPermissionsIfNeeded() {
        val permissionsNeeded = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN)
            }
        }

        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toTypedArray(), 1)
        } else {
            enableBluetooth()
        }
    }

    private fun enableBluetooth() {
        val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            startActivityForResult(enableBtIntent, 1)
        }
    }

    private fun drawGridOnMap() {
        val relativeLayout = findViewById<RelativeLayout>(R.id.map_layout)
        relativeLayout.removeAllViews()
        val imageView = ImageView(this)
        val bitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()

        // Desenhar a planta de fundo
        val background = BitmapFactory.decodeResource(resources, R.drawable.output)
        val scaleFactor = Math.min(bitmap.width.toFloat() / background.width, bitmap.height.toFloat() / background.height)
        val scaledBitmap = Bitmap.createScaledBitmap(background, (background.width * scaleFactor).toInt(), (background.height * scaleFactor).toInt(), true)
        canvas.drawBitmap(scaledBitmap, 0f, 0f, paint)

        // Configurar a pintura para o texto
        paint.color = Color.RED
        paint.style = Paint.Style.STROKE
        paint.textSize = 20f

        // Desenhar a grelha e os números
        val gridSize = 1000f / size // Ajustar o tamanho da grade
        for (i in 0 until size) {
            for (j in 0 until size) {
                val x = i * gridSize
                val y = j * gridSize
                canvas.drawRect(x, y, x + gridSize, y + gridSize, paint)
                canvas.drawText("$i,$j", x + 10, y + 20, paint)
            }
        }

        imageView.setImageBitmap(bitmap)
        relativeLayout.addView(imageView)
    }

    private fun filterRssi(identifier: String, rssi: Double): Double {
        val readings = rssiReadings.getOrPut(identifier) { mutableListOf() }
        readings.add(rssi)
        if (readings.size > windowSize) readings.removeAt(0)
        return readings.average()
    }

    override fun onBeaconServiceConnect() {
        beaconManager.addRangeNotifier { beacons, region ->
            runOnUiThread {
                val textView = findViewById<TextView>(R.id.textView)
                val mapLayout = findViewById<RelativeLayout>(R.id.map_layout)

                val beaconDistances = beacons.associate {
                    val identifier = beaconIdentifiers[it.id2.toInt() to it.id3.toInt()] ?: "Desconhecido"
                    identifier to filterRssi(identifier, it.distance)
                }

                val position = calculatePosition(beaconDistances)
                textView.text = buildString {
                    append("Beacons Detectados:\n")
                    beacons.forEach { beacon ->
                        val identifier = beaconIdentifiers[beacon.id2.toInt() to beacon.id3.toInt()] ?: "Desconhecido"
                        append("Identificador: $identifier, UUID: ${beacon.id1}, Major: ${beacon.id2}, Minor: ${beacon.id3}, Distância: ${String.format("%.2f", beacon.distance)}m\n")
                    }
                }

                position?.let {
                    currentEstimatedPosition = Pair(it.x.toInt(), it.y.toInt())
                    updateCurrentLocationOnMap(currentEstimatedPosition!!)
                }
            }
        }

        try {
            beaconManager.startRangingBeaconsInRegion(Region("myBeaconRegion", null, null, null))
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }

    private fun updateCurrentLocationOnMap(position: Pair<Int, Int>) {
        val mapLayout = findViewById<RelativeLayout>(R.id.map_layout)
        val escala = 1000f / originalGridSize // Usar o tamanho da matriz original para escalar

        currentLocationView?.let { mapLayout.removeView(it) }

        val x = position.first * escala
        val y = position.second * escala

        val estimatedPositionIcon = ImageView(this@MainActivity)
        estimatedPositionIcon.setImageResource(R.drawable.baseline_location_on_24)
        val layoutParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        )
        layoutParams.leftMargin = x.toInt()
        layoutParams.topMargin = y.toInt()
        estimatedPositionIcon.layoutParams = layoutParams
        mapLayout.addView(estimatedPositionIcon)
        currentLocationView = estimatedPositionIcon
    }

    private fun drawWeightOnGrid(x: Int, y: Int, weight: Int, mapLayout: RelativeLayout) {
        // Remover ícone anterior se existir
        weightIcons[Pair(x, y)]?.let { mapLayout.removeView(it) }

        // Criar e adicionar novo ícone de peso
        val weightIcon = TextView(this)
        weightIcon.text = weight.toString()
        weightIcon.setTextColor(Color.BLACK)
        weightIcon.textSize = 14f
        val layoutParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        )
        layoutParams.leftMargin = (x * 1000f / size).toInt()
        layoutParams.topMargin = (y * 1000f / size).toInt()
        weightIcon.layoutParams = layoutParams
        mapLayout.addView(weightIcon)

        // Armazenar a referência ao novo ícone de peso
        weightIcons[Pair(x, y)] = weightIcon
    }

    private fun calculatePosition(beaconDistances: Map<String, Double>): Position? {
        if (beaconDistances.size < 3) return null // Precisamos de pelo menos 3 beacons

        val positions = beaconDistances.keys.mapNotNull { beaconPositions[it] }
        val distances = beaconDistances.values.toList()

        if (positions.size < 3) return null // Verifique se temos posições suficientes

        val weights = distances.map { 1 / (it * it) } // Pesos inversamente proporcionais ao quadrado da distância
        val sumWeights = weights.sum()

        val x = positions.zip(weights).sumByDouble { it.first.x * it.second } / sumWeights
        val y = positions.zip(weights).sumByDouble { it.first.y * it.second } / sumWeights

        return Position(x, y)
    }

    private fun findShortestPath(matrix: Array<IntArray>, start: Pair<Int, Int>, end: Pair<Int, Int>): List<Pair<Int, Int>> {
        val n = matrix.size
        val distances = Array(n) { IntArray(n) { Int.MAX_VALUE } }
        distances[start.first][start.second] = matrix[start.first][start.second]

        val previous = Array(n) { arrayOfNulls<Pair<Int, Int>?>(n) }
        val visited = Array(n) { BooleanArray(n) { false } }
        val queue = mutableListOf<Pair<Int, Int>>()
        queue.add(start)

        while (queue.isNotEmpty()) {
            val (x, y) = queue.removeAt(0)
            visited[x][y] = true

            val neighbors = getNeighbors(x, y, matrix)
            for ((nx, ny) in neighbors) {
                val newDistance = distances[x][y] + matrix[nx][ny]
                if (newDistance < distances[nx][ny]) {
                    distances[nx][ny] = newDistance
                    previous[nx][ny] = Pair(x, y)
                    queue.add(Pair(nx, ny))
                }
            }
        }

        val shortestPath = mutableListOf<Pair<Int, Int>>()
        var current = end
        while (current != start) {
            shortestPath.add(current)
            current = previous[current.first][current.second] ?: break
        }
        shortestPath.add(start)
        shortestPath.reverse()

        return if (distances[end.first][end.second] == Int.MAX_VALUE) {
            Log.e("MainActivity", "No path found")
            emptyList()
        } else {
            Log.d("MainActivity", "Path found with distance: ${distances[end.first][end.second]}")
            Log.d("MainActivity", "Path: $shortestPath")
            shortestPath
        }
    }

    private fun getNeighbors(x: Int, y: Int, matrix: Array<IntArray>): List<Pair<Int, Int>> {
        val neighbors = mutableListOf<Pair<Int, Int>>()
        val directions = listOf(Pair(-1, 0), Pair(0, -1), Pair(1, 0), Pair(0, 1))

        for ((dx, dy) in directions) {
            val nx = x + dx
            val ny = y + dy
            if (nx in matrix.indices && ny in matrix[0].indices && matrix[nx][ny] > 0) {
                neighbors.add(Pair(nx, ny))
            }
        }
        Log.d("MainActivity", "Neighbors of ($x, $y): $neighbors")
        return neighbors
    }

    fun onCalculatePathClick(view: View) {
        val endPointInput = findViewById<EditText>(R.id.end_point).text.toString()
        val endPoint = endPointInput.split(",").let { Pair(it[0].toInt(), it[1].toInt()) }

        val startPoint = getCurrentLocation() ?: run {
            Toast.makeText(this, "Current location not available.", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d("MainActivity", "Start Point: $startPoint, End Point: $endPoint")

        val matrix = generateMatrixFromWeights(weights, size)

        Log.d("MainActivity", "Weight Matrix: ${matrix.contentDeepToString()}")

        if (startPoint.first !in 0 until size || startPoint.second !in 0 until size ||
            endPoint.first !in 0 until size || endPoint.second !in 0 until size) {
            Toast.makeText(this, "Points are out of bounds.", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isValidPoint(startPoint, matrix) || !isValidPoint(endPoint, matrix)) {
            Toast.makeText(this, "Start or end point is not valid.", Toast.LENGTH_SHORT).show()
            return
        }

        val path = findShortestPath(matrix, startPoint, endPoint)
        if (path.isEmpty()) {
            Toast.makeText(this, "No path found.", Toast.LENGTH_SHORT).show()
        } else {
            drawPathOnMap(path)
        }
    }

    private fun generateMatrixFromWeights(weights: IntArray, size: Int): Array<IntArray> {
        val matrix = Array(size) { IntArray(size) }
        for (i in weights.indices) {
            matrix[i / size][i % size] = weights[i]
        }
        return matrix
    }

    private fun isValidPoint(point: Pair<Int, Int>, matrix: Array<IntArray>): Boolean {
        return matrix[point.first][point.second] != 0
    }

    private fun getCurrentLocation(): Pair<Int, Int>? {
        return currentEstimatedPosition?.let { (x, y) ->
            val escala = originalGridSize / size.toFloat()
            Pair((x / escala).toInt(), (y / escala).toInt())
        }
    }

    private fun drawPathOnMap(path: List<Pair<Int, Int>>) {
        val mapLayout = findViewById<RelativeLayout>(R.id.map_layout)
        val escala = 1000f / size

        // Remove previous path views
        pathViews.forEach { mapLayout.removeView(it) }
        pathViews.clear()

        for ((x, y) in path) {
            val pathView = View(this)
            pathView.setBackgroundColor(Color.BLUE)
            val layoutParams = RelativeLayout.LayoutParams((escala / 2).toInt(), (escala / 2).toInt())
            layoutParams.leftMargin = (x * escala).toInt()
            layoutParams.topMargin = (y * escala).toInt()
            pathView.layoutParams = layoutParams
            mapLayout.addView(pathView)
            pathViews.add(pathView)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        beaconManager.unbind(this)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            enableBluetooth()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == RESULT_OK) {
            // Bluetooth ativo
        }
    }
}