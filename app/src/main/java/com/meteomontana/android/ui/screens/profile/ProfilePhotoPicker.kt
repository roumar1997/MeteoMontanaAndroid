package com.meteomontana.android.ui.screens.profile

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.meteomontana.android.R
import com.meteomontana.android.util.AppText
import com.yalantis.ucrop.UCrop
import java.io.File

/**
 * Elegir una foto y recortarla en cuadrado (círculo de foto de perfil) con uCrop. Devuelve la función que abre el
 * selector; al terminar el recorte se llama a [onCropped] con el fichero ya recortado. La usan Editar perfil y el
 * propio perfil (tocar tu foto para cambiarla, Álvaro 2026-10-23), para no duplicar la configuración del recorte.
 */
@Composable
fun rememberProfilePhotoPicker(onCropped: (Uri) -> Unit): () -> Unit {
    val context = LocalContext.current

    // uCrop devuelve un Uri al fichero ya recortado.
    val cropLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { UCrop.getOutput(it) }?.let(onCropped)
        }
    }

    // Picker normal — al elegir, lanzamos uCrop con la foto seleccionada.
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val destFile = File(context.cacheDir, "profile_crop_${System.currentTimeMillis()}.jpg")
            val destUri = Uri.fromFile(destFile)
            val intent = UCrop.of(uri, destUri)
                .withAspectRatio(1f, 1f)
                .withMaxResultSize(1024, 1024)
                .withOptions(UCrop.Options().apply {
                    setCircleDimmedLayer(true)        // máscara circular
                    setShowCropFrame(false)
                    setShowCropGrid(false)
                    setHideBottomControls(false)
                    setFreeStyleCropEnabled(false)
                    setCompressionQuality(85)
                    setToolbarTitle(AppText.get(R.string.edit_profile_screen_v3_recortar_foto))
                    // Colores Cumbre: barra de papel y el ✓/✕ en tinta encima
                    // (con los de serie, blancos sobre blanco, el ✓ apenas se
                    // distinguía). DÓNDE se dibuja la barra lo arregla
                    // Theme.MeteoMontana.Crop — ver el AndroidManifest.
                    setToolbarColor(ContextCompat.getColor(context, R.color.crop_toolbar))
                    setStatusBarColor(ContextCompat.getColor(context, R.color.crop_toolbar))
                    setToolbarWidgetColor(ContextCompat.getColor(context, R.color.crop_ink))
                    setActiveControlsWidgetColor(ContextCompat.getColor(context, R.color.crop_terra))
                })
                .getIntent(context)
                // uCrop apunta a SU PROPIA UCropActivity por defecto; se
                // retarget a CumbreCropActivity (misma librería, con el
                // fix de insets). Los extras del intent no se tocan.
                .setClass(context, CumbreCropActivity::class.java)
            cropLauncher.launch(intent)
        }
    }
    return { picker.launch("image/*") }
}
