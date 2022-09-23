package com.example.faceentry;

import androidx.appcompat.app.AppCompatActivity;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.objdetect.FaceDetectorYN;
import org.opencv.core.Size;
import org.opencv.objdetect.FaceRecognizerSF;
import org.opencv.imgproc.Imgproc;

import com.example.faceentry.databinding.ActivityMainBinding;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, FileSelectionDialog.OnFileSelectListener {

    // Used to load the 'faceentry' library on application startup.
    static {
        System.loadLibrary("faceentry");
    }

    static {
        System.loadLibrary("opencv_java4");
    }

    // 定数
    private static final int REQUEST_PERMISSION_READ_WRITE_EXTERNAL_STORAGE = 1; // 外部ストレージ読み書きパーミッション要求時の識別コード

    // メンバー変数
    private String m_strInitialDir = Environment.getExternalStorageDirectory().getPath();    // 初期フォルダ
    private final String dataDir = "/storage/emulated/0/Pictures/";             //特徴点の書き出しフォルダ
    private final String YNFilename = "yunet.onnx";                             // YuNetの学習済みのモデル
    private final String SFFilename = "face_recognizer_fast.onnx";              // SFaceの学習済みのモデル
    private final String DFilename  = "Detect_";                                // 検出対象の顔画像（切り抜き後）

    private final double SUBJECT_WIDTH = 640.0;

    private Mat img;

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        findViewById(R.id.select).setOnClickListener(this);
        findViewById(R.id.register).setOnClickListener(this);
        findViewById(R.id.clear).setOnClickListener(this);

    }

    /**
     * A native method that is implemented by the 'faceentry' native library,
     * which is packaged with this application.
     */
    public native void featureExport(long objFaceFeature, String employeeNumber, String name, String dataDir);

    public void onClick(View v) {
        if (v.getId() == R.id.select) {
            //画像選択
            TextView tv = (TextView)findViewById(R.id.picturePath);
            tv.setText("select");

            // ダイアログオブジェクト
            FileSelectionDialog dlg = new FileSelectionDialog( this, this, "jpg" );
            dlg.show( new File( m_strInitialDir ) );

        } else if (v.getId() == R.id.clear) {
            //入力内容をクリア
            EditText et = (EditText)findViewById(R.id.employeeNumber);
            et.getEditableText().clear();

            et = (EditText)findViewById(R.id.name);
            et.getEditableText().clear();

            ImageView myImage= findViewById(R.id.facePicture);
            myImage.setImageResource(R.drawable.init);

            TextView tv = (TextView)findViewById(R.id.picturePath);
            tv.setText("未設定");

            myImage= findViewById(R.id.faceDetection);
            myImage.setImageResource(R.drawable.init);

        } else if (v.getId() == R.id.register) {
            //登録

            if( checkEmptyEntryInfo() == false) {
                //必要情報が入力されていない
                Toast.makeText( this, "登録者情報が入力されていません", Toast.LENGTH_SHORT ).show();
                return;
            }

            //----------------------------------------------
            // 学習モデルの読み込み
            //   ・YuNetの学習済みのモデル
            //   ・SFaceの学習済みのモデル
            //----------------------------------------------
            File yunetFile = new File(getFilesDir().getPath() + File.separator + YNFilename);
            if (!yunetFile.exists()) {
                try (InputStream inputStream = getAssets().open(YNFilename);
                     FileOutputStream fileOutputStream = new FileOutputStream(yunetFile, false)) {
                    byte[] buffer = new byte[1024];
                    int read;
                    while ((read = inputStream.read(buffer)) != -1) {
                        fileOutputStream.write(buffer, 0, read);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            FaceDetectorYN face_detector = FaceDetectorYN.create(yunetFile.getAbsolutePath(), "", new Size(0, 0));

            File sfaceFile = new File(getFilesDir().getPath() + File.separator + SFFilename);
            if (!sfaceFile.exists()) {
                try (InputStream inputStream = getAssets().open(SFFilename);
                     FileOutputStream fileOutputStream = new FileOutputStream(sfaceFile, false)) {
                    byte[] buffer = new byte[1024];
                    int read;
                    while ((read = inputStream.read(buffer)) != -1) {
                        fileOutputStream.write(buffer, 0, read);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            FaceRecognizerSF face_recognizer = FaceRecognizerSF.create(sfaceFile.getAbsolutePath(), "");
            //----------------------------------------
            // 入力画像のサイズを変更（処理時間を考慮）
            // ＜注意＞
            //　　サイズを縮小すると検出率が下がる
            //----------------------------------------
            Imgproc.resize( img, img, new Size(), 0.5, 0.5 );

            //----------------------------------------
            // 入力画像のサイズの指定
            //----------------------------------------
            face_detector.setInputSize( new Size( img.cols(), img.rows() ) );
            //----------------------------------------
            // 顔の検出
            //----------------------------------------
            int ret;
            Mat dtfaces = new Mat();

            ret = face_detector.detect( img, dtfaces );
            if( ret == 0 )
            {
                Log.d("debug", "Error detect");
                return;
            }
            if(dtfaces.empty())
            {
                Log.d("debug", "Error Detect Empty");
                return;
            }

            //----------------------------------------
            // 顔を切り抜く
            //----------------------------------------
            Mat aligned_face = new Mat();
            boolean retb;

            // 顔を切り抜く
            face_recognizer.alignCrop(img, dtfaces, aligned_face);

            //Context context = getApplicationContext();
            //String dataDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES).getPath() + File.separator;

            // 顔画像を保存
            EditText et_employeeNumber = (EditText)findViewById(R.id.employeeNumber);
            String DfileName = dataDir + DFilename + et_employeeNumber.getText() + ".jpg";

            retb = Imgcodecs.imwrite(DfileName, aligned_face );

            if( retb == false )
            {
                Log.d("debug", "Error 顔画像保存失敗");
            }

            // 画像表示
            Bitmap bmp_img = Bitmap.createBitmap(aligned_face.width(), aligned_face.height(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(aligned_face, bmp_img);
            ImageView imgV = (android.widget.ImageView)findViewById(R.id.faceDetection);
            imgV.setImageBitmap(bmp_img);

            //----------------------------------------------
            // 特徴の抽出
            //----------------------------------------------
            Mat face_feature = new Mat();
            face_recognizer.feature(aligned_face, face_feature);

            //----------------------------------------------
            // 特徴の保存
            //----------------------------------------------
            EditText et_name = (EditText)findViewById(R.id.name);

            featureExport(face_feature.getNativeObjAddr(),
                    et_employeeNumber.getText().toString(),
                    et_name.getText().toString(),
                    dataDir);

            Toast.makeText( this, "登録完了しました", Toast.LENGTH_SHORT ).show();

        }
    }

    //----------------------------------------------------
    // 必要な登録(社員番号、名前、登録画像)が入力されているかチェック
    // true:必要な情報が入力されている　false:未入力の項目あり
    //----------------------------------------------------
    private boolean checkEmptyEntryInfo() {
        boolean ret = true;

        //社員番号入力チェック
        EditText et = (EditText)findViewById(R.id.employeeNumber);
        if(et.getEditableText().toString().equals("")) {
            ret = false;
        }

        //名前入力チェック
        et = (EditText)findViewById(R.id.name);
        if(et.getEditableText().toString().equals("")) {
            ret = false;
        }

        TextView tv = (TextView)findViewById(R.id.picturePath);
        if(tv.getText().toString().equals("未設定")) {
            ret = false;
        }
        return ret;

    }

    // ファイルが選択されたときに呼び出される関数
    public void onFileSelect( File file )
    {
        m_strInitialDir = file.getParent();
        if (file.exists()) {
            // 画像が存在
            img = Imgcodecs.imread(file.getPath());

            //-----------------------------------------
            // 対象者画像の表示(編集前の画像）
            //-----------------------------------------
            // 640x480サイズに編集しviweImageに表示
            double  rt = (double)SUBJECT_WIDTH / img.cols();

            Mat img_sub = img.clone();
            Imgproc.resize( img_sub, img_sub, new Size(img_sub.width() * rt, img_sub.height() * rt) );
            Bitmap bmp_img = Bitmap.createBitmap(img_sub.width(), img_sub.height(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(img_sub, bmp_img);
            ImageView imgV = (android.widget.ImageView)findViewById(R.id.facePicture);
            imgV.setImageBitmap(bmp_img);

            TextView tv = (TextView)findViewById(R.id.picturePath);
            tv.setText(file.getPath());
        }
    }

    // 初回表示時、および、ポーズからの復帰時
    @Override
    protected void onResume()
    {
        super.onResume();

        // 外部ストレージ読み込みパーミッション要求
        requestReadWriteExternalStoragePermission();
    }

    // 外部ストレージ読み書きパーミッション要求
    private void requestReadWriteExternalStoragePermission()
    {
        if(PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission( this, Manifest.permission.READ_EXTERNAL_STORAGE ) &&
                PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission( this, Manifest.permission.WRITE_EXTERNAL_STORAGE ))
        {    // パーミッションは付与されている
            return;
        }
        // パーミッションは付与されていない。
        // パーミッションリクエスト
        ActivityCompat.requestPermissions( this,
                new String[]{
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                },
                REQUEST_PERMISSION_READ_WRITE_EXTERNAL_STORAGE );
    }

    // パーミッション要求ダイアログの操作結果
    @Override
    public void onRequestPermissionsResult( int requestCode, String[] permissions, int[] grantResults ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_PERMISSION_READ_WRITE_EXTERNAL_STORAGE:
                if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    // 許可されなかった場合
                    Toast.makeText(this, "Permission denied.", Toast.LENGTH_SHORT).show();
                    finish();    // アプリ終了宣言
                    return;
                }
                break;
            default:
                break;
        }
    }

}