package com.jarvismini.engine.ai

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File

object ModelPathManager {
    private const val TAG = "ModelPathManager"
    private const val MODELS_FOLDER_NAME = "JarvisModels"
    
    /**
     * Get the correct models directory path using Android's proper APIs
     * This works across all Android versions and handles symlinks correctly
     */
    fun getModelsDirectory(context: Context): File {
        // Use Environment.getExternalStorageDirectory() which properly resolves to /storage/emulated/0
        val externalStorage = Environment.getExternalStorageDirectory()
        val modelsDir = File(externalStorage, MODELS_FOLDER_NAME)
        
        Log.d(TAG, "Models directory path: ${modelsDir.absolutePath}")
        
        // Create directory if it doesn't exist
        if (!modelsDir.exists()) {
            val created = modelsDir.mkdirs()
            Log.d(TAG, "Models directory created: $created")
        }
        
        return modelsDir
    }
    
    /**
     * List all .gguf model files in the models directory
     */
    fun listModelFiles(context: Context): List<File> {
        val modelsDir = getModelsDirectory(context)
        
        if (!modelsDir.exists() || !modelsDir.isDirectory) {
            Log.w(TAG, "Models directory does not exist: ${modelsDir.absolutePath}")
            return emptyList()
        }
        
        val modelFiles = modelsDir.listFiles { file ->
            file.isFile && file.extension.equals("gguf", ignoreCase = true)
        }?.toList() ?: emptyList()
        
        Log.d(TAG, "Found ${modelFiles.size} model files")
        modelFiles.forEach { 
            Log.d(TAG, "  - ${it.name} (${it.length() / 1024 / 1024}MB)")
        }
        
        return modelFiles
    }
    
    /**
     * Find a specific model file by name (case-insensitive)
     */
    fun findModel(context: Context, modelName: String): File? {
        val models = listModelFiles(context)
        return models.find { 
            it.nameWithoutExtension.equals(modelName, ignoreCase = true)
        }
    }
    
    /**
     * Get the default model (first .gguf file found, or a specific preferred model)
     */
    fun getDefaultModel(context: Context, preferredName: String? = null): File? {
        val models = listModelFiles(context)
        
        if (models.isEmpty()) {
            Log.w(TAG, "No models found in ${getModelsDirectory(context).absolutePath}")
            return null
        }
        
        // Try to find preferred model first
        preferredName?.let { name ->
            models.find { it.nameWithoutExtension.equals(name, ignoreCase = true) }?.let {
                Log.d(TAG, "Using preferred model: ${it.name}")
                return it
            }
        }
        
        // Return first model as default
        Log.d(TAG, "Using default model: ${models[0].name}")
        return models[0]
    }
    
    /**
     * Check if models directory exists and has at least one model
     */
    fun hasModels(context: Context): Boolean {
        return listModelFiles(context).isNotEmpty()
    }
    
    /**
     * Get a user-friendly error message if no models are found
     */
    fun getNoModelsErrorMessage(context: Context): String {
        val path = getModelsDirectory(context).absolutePath
        return "No AI models found. Please place .gguf model files in:\n$path"
    }
}
