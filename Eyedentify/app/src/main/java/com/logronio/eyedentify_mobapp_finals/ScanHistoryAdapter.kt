package com.logronio.eyedentify_mobapp_finals

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.logronio.eyedentify_mobapp_finals.databinding.ItemScanHistoryBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScanHistoryAdapter(
    private val onItemClick: (ScanDatabase) -> Unit
) : ListAdapter<ScanDatabase, ScanHistoryAdapter.ViewHolder>(DIFF_CALLBACK) {

    inner class ViewHolder(
        private val binding: ItemScanHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(scanResult: ScanDatabase) {
            loadImage(scanResult)
            setResultText(scanResult)
            setDateTime(scanResult)

            itemView.setOnClickListener {
                onItemClick(scanResult)
            }
        }

        private fun loadImage(scanResult: ScanDatabase) {
            (itemView.context as? LifecycleOwner)?.lifecycleScope?.launch {
                try {
                    val bitmap = withContext(Dispatchers.IO) {
                        val uri = scanResult.imageUri.toUri()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            ImageDecoder.decodeBitmap(
                                ImageDecoder.createSource(
                                    itemView.context.contentResolver,
                                    uri
                                )
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            MediaStore.Images.Media.getBitmap(
                                itemView.context.contentResolver,
                                uri
                            )
                        }
                    }
                    binding.historyImageView.setImageBitmap(bitmap)
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading image", e)
                    binding.historyImageView.setImageResource(R.drawable.ic_image_placeholder)
                }
            }
        }

        private fun setResultText(scanResult: ScanDatabase) {
            val parts = mutableListOf<String>()
            scanResult.detectedText.takeIf { it.isNotBlank() }?.let {
                parts += "Text: $it"
            }
            scanResult.detectedObjects.takeIf { it.isNotEmpty() }?.let {
                parts += "Objects: ${it.joinToString(", ")}"
            }
            binding.historyTextView.text = if (parts.isNotEmpty()) parts.joinToString("\n") else "No results"
        }

        private fun setDateTime(scanResult: ScanDatabase) {
            binding.historyDateView.text = DATE_FORMAT.format(scanResult.timestamp)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemScanHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private const val TAG = "ScanHistoryAdapter"
        private val DATE_FORMAT = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ScanDatabase>() {
            override fun areItemsTheSame(oldItem: ScanDatabase, newItem: ScanDatabase) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: ScanDatabase, newItem: ScanDatabase) =
                oldItem == newItem
        }
    }
}
