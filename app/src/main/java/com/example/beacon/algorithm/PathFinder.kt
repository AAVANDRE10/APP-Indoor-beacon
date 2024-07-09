package com.example.beacon.algorithm

import android.util.Log

class PathFinder {
    fun findShortestPath(matrix: Array<IntArray>, start: Pair<Int, Int>, end: Pair<Int, Int>): List<Pair<Int, Int>> {
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
            Log.e("PathFinder", "No path found")
            emptyList()
        } else {
            Log.d("PathFinder", "Path found with distance: ${distances[end.first][end.second]}")
            Log.d("PathFinder", "Path: $shortestPath")
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
        Log.d("PathFinder", "Neighbors of ($x, $y): $neighbors")
        return neighbors
    }
}