package com.logronio.eyedentify_mobapp_finals

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID

/**
 * @param firestore makes testing easier—inject a mock if needed.
 */
class ScanRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val scansCollection = firestore.collection(COLLECTION_SCANS)

    // ----------  C  R  U  D  ----------

    suspend fun insert(scanResult: ScanDatabase): String {
        val id = scanResult.id.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()

        val data = mapOf(
            "id"              to id,
            "imageUri"        to scanResult.imageUri,
            "detectedText"    to scanResult.detectedText,
            "detectedObjects" to scanResult.detectedObjects,
            "timestamp"       to scanResult.timestamp
        )

        return try {
            scansCollection.document(id).set(data).await()
            Log.d(TAG, "✅ Scan saved successfully (id=$id)")
            id
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to save scan (id=$id): ${e.message}", e)
            throw e
        }
    }

    fun getAllScans(): Flow<List<ScanDatabase>> = callbackFlow {
        val listener = scansCollection
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e(TAG, "Listener error", err)
                    close(err)
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(ScanDatabase::class.java)?.copy(id = doc.id)
                }.orEmpty()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    suspend fun getScanById(id: String): ScanDatabase? =
        scansCollection.document(id).get().await().let { doc ->
            if (doc.exists()) doc.toObject(ScanDatabase::class.java)?.copy(id = doc.id)
            else null
        }

    suspend fun update(scan: ScanDatabase) {
        require(scan.id.isNotBlank()) { "Scan.id must not be blank for update()" }
        scansCollection.document(scan.id).set(scan).await()
        Log.d(TAG, "✅ Scan updated (id=${scan.id})")
    }

    suspend fun delete(scan: ScanDatabase) {
        if (scan.id.isBlank()) return
        scansCollection.document(scan.id).delete().await()
        Log.d(TAG, "✅ Scan deleted (id=${scan.id})")
    }

    suspend fun deleteAll() {
        scansCollection.get().await().documents.forEach { it.reference.delete().await() }
        Log.d(TAG, "✅ All scans deleted")
    }

    suspend fun getCount(): Int = scansCollection.get().await().size()

    // ----------  E X T R A   Q U E R I E S  ----------

    fun getScansByText(query: String): Flow<List<ScanDatabase>> = callbackFlow {
        val listener = scansCollection
            .whereGreaterThanOrEqualTo("detectedText", query)
            .whereLessThanOrEqualTo("detectedText", query + '\uf8ff')
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                val list = snap?.documents?.mapNotNull { it.toObject(ScanDatabase::class.java)?.copy(id = it.id) }
                    .orEmpty()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    fun getScansByDateRange(start: Date, end: Date): Flow<List<ScanDatabase>> = callbackFlow {
        val listener = scansCollection
            .whereGreaterThanOrEqualTo("timestamp", start)
            .whereLessThanOrEqualTo("timestamp", end)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                val list = snap?.documents?.mapNotNull { it.toObject(ScanDatabase::class.java)?.copy(id = it.id) }
                    .orEmpty()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    companion object {
        private const val TAG = "ScanRepository"
        private const val COLLECTION_SCANS = "scan_history"
    }
}
