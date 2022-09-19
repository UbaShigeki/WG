#include <jni.h>
#include <string>
#include <stdio.h>
#include <iostream>
#include <fstream>
#include <android/log.h>
#include <vector>

#include <opencv2/opencv.hpp>
#include <opencv2/core/persistence.hpp>

using namespace std;

extern "C" JNIEXPORT void JNICALL
Java_com_example_faceentry_MainActivity_featureExport(
        JNIEnv* env,
        jobject, /* this */
        jlong   objFaceFeature ,
        jstring employeeNumber,
        jstring name,
        jstring dataDir) {

    static const string FFolder    = "/storage/emulated/0/Pictures/";          // 特徴点フォルダ（128次元）
    static const string FFilename  = "Feature.yml";                            // 検出対象の顔画像（編集前）
    static const string FFilename_Name  = "Feature_Name.dat";                  // 検出対象の個人情報
    static const string Featurename    = "Feature_";                           // 個人特定用のタグ名（仮、複数人登録時は番号の編集が必要）

    const char* number_str = env->GetStringUTFChars(employeeNumber, 0);
    const char* name_str = env->GetStringUTFChars(name, 0);
    const char* dataDir_str = env->GetStringUTFChars(dataDir, 0);

    string ft = Featurename + number_str;
    string dataDirPath = dataDir_str;

    cv::Mat* face_feature = (cv::Mat*) objFaceFeature;
    //cv::FileStorage fs_write( FFolder+FFilename, cv::FileStorage::WRITE);
    cv::FileStorage fs_write( dataDirPath + FFilename, cv::FileStorage::APPEND);

    fs_write << ft << *face_feature;
    fs_write.release();

    //----------------------------------------------
    // 対象者情報の保存(binary)
    //----------------------------------------------
    //fstream fs;

    __android_log_write(ANDROID_LOG_DEBUG, "debug", "CCCCCC");
    ofstream ofs;
    //ofs.open(FFolder+FFilename_Name, ios::binary|ios::app);
    ofs.open(dataDirPath + FFilename_Name, ios::binary|ios::app);
    ofs<< ft <<endl;
    ofs<< name <<endl;
    ofs.close();

    env->ReleaseStringUTFChars(name, name_str);
    env->ReleaseStringUTFChars(employeeNumber, number_str);
    env->ReleaseStringUTFChars(dataDir, dataDir_str);
}