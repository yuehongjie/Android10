package com.bailitop.gallery.ui.activity

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bailitop.gallery.bean.PhotoResult
import com.bailitop.gallery.constant.GalleryResult
import com.bailitop.gallery.ext.hasQ
import com.bailitop.gallery.scan.SingleMediaScanner
import java.io.File

/**
 * 拍照 是一个完全透明的 Activity，主题设置了点击透明区域会消失（对用户不可见，防止用户假死在这个页面）
 */
class TakePhotoActivity : AppCompatActivity(), SingleMediaScanner.SingleScannerListener {

    private var mPhotoUri: Uri ?= null
    private var singleMediaScanner: SingleMediaScanner? = null

    companion object {
        // 申请相机权限的 requestCode
        private const val PERMISSION_REQUEST_CODE = 0x01

        // 拍照的 requestCode
        private const val CAMERA_REQUEST_CODE = 0x02
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //setContentView(R.layout.activity_take_photo_gallery)

        //透明状态栏
        window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)

        checkPermissionAndCamera()

    }

    /**
     * 检查权限并拍照
     */
    private fun checkPermissionAndCamera() {

        //检查相机权限
        val hasCameraPermission = ContextCompat.checkSelfPermission(application, Manifest.permission.CAMERA)
        val hasStoragePermission = ContextCompat.checkSelfPermission(application, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (hasCameraPermission == PackageManager.PERMISSION_GRANTED && hasStoragePermission == PackageManager.PERMISSION_GRANTED) {
            //有权限，调用相机拍照
            openCamera()
        }else {
            //没有权限，申请权限
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE), PERMISSION_REQUEST_CODE)
        }
    }

    /**
     * 处理权限申请回调
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        //相机权限申请
        if (requestCode == PERMISSION_REQUEST_CODE) {

            var isAllGranted = true
            for (grant in grantResults) {
                if (grant == PackageManager.PERMISSION_DENIED) {
                    isAllGranted = false
                    break
                }
            }

            if (isAllGranted) {
                // 同意所有需要的权限，调用相机拍照
                openCamera()
            }else {
                Toast.makeText(this, "没有权限", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CAMERA_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                scanImage()
            }else {
                finish()
            }
        }
    }

    private fun scanImage() {

        Log.d("TakePhotoActivity", "拍照成功，扫描照片")

        uriToPath(mPhotoUri)?.let {
            //先取消上次的扫描
            singleMediaScanner?.disconnect()
            singleMediaScanner = SingleMediaScanner(this, it, this@TakePhotoActivity)
        }
    }

    // ----------------------  SingleScannerListener start ------------------------
    override fun onScanStart() {
        Log.d("TakePhotoActivity", "扫描开始")
    }

    override fun onScanCompleted(path: String?, uri: Uri?) {
        Log.d("TakePhotoActivity", "扫描完成 path: $path   uri: $uri")
        resultFinish()
    }
    // ----------------------  SingleScannerListener end ------------------------


    /**
     * 携带数据返回上级页面
     */
    private fun resultFinish() {
        val resultIntent = Intent()
        val photo = PhotoResult(mPhotoUri)
        resultIntent.putExtra(GalleryResult.KEY_RESULT_TAKE_PHOTO, photo)
        setResult(GalleryResult.RESULT_CODE_TAKE_PHOTO, resultIntent)
        finish()
    }


    private fun openCamera() {

        val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        //判断是否有相机
        if (captureIntent.resolveActivity(packageManager) != null) {

            mPhotoUri = createImageUri()

            Log.d("TakePhotoActivity", "photoUri: $mPhotoUri")

            if (mPhotoUri != null) {
                captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, mPhotoUri)
                startActivityForResult(captureIntent, CAMERA_REQUEST_CODE)
            }else {
                finish()
            }

        }else {
            finish()
        }

    }


    /**
     * 为了兼容 Android 10 及以后，建议开发者不再 在根目录中创建自己的文件夹，
     * 而是使用自己的私有外部存储或者公共的外部媒体存储区域
     */
    private fun createImageUri(): Uri? {

        val imageFile = createImageFile()

        Log.d("TakePhotoActivity", "imageFile path: ${imageFile.path}")

        val contentValues = ContentValues().apply {
            if (hasQ()) {
                put(MediaStore.MediaColumns.DISPLAY_NAME, imageFile.name)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM)
            }else {
                put(MediaStore.MediaColumns.DATA, imageFile.path)
            }
        }

        return if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        } else {
            contentResolver.insert(MediaStore.Images.Media.INTERNAL_CONTENT_URI, contentValues)
        }

    }

    private fun createImageFile(): File {
        val fileName = "${System.currentTimeMillis()}.jpg"
        if (hasQ()) {
            return File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).path, fileName)
        }

        val storageDir = if (Environment.MEDIA_MOUNTED == Environment.getExternalStorageState() || !Environment.isExternalStorageRemovable()){
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).path
        }else {
            externalCacheDir?.path
        }

        return File(storageDir, fileName)
    }

    /**
     * uri 转真实路径
     */
    private fun uriToPath(uri: Uri?): String? {
        if (uri == null) return null
        contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null).use {
            //use 扩展会自动关闭 cursor
            val cursor = it ?: return null
            val dataColumnIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
            while (cursor.moveToNext()) {
                return cursor.getString(dataColumnIndex)
            }
        }

        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        singleMediaScanner?.disconnect()
    }
}
