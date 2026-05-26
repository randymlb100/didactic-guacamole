package com.lotterynet.pro.core.export

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri

fun saveBitmapToCache(context: Context, bitmap: Bitmap): Uri {
    return NativeBitmapExport.saveBitmapToCache(context, bitmap)
}

fun shareResultadosWhatsApp(context: Context, bitmap: Bitmap): NativeBitmapExport.ExportActionResult {
    return NativeBitmapExport.shareResultadosWhatsApp(context, bitmap)
}
