#include <jni.h>
#include <sys/types.h>
#include <stdbool.h>
#include <unistd.h>
#include <pthread.h>
#include <stdio.h>

//
// Created by maks on 17.02.21.
//
static JavaVM *_______jvm;
static volatile jmethodID _______method;
static volatile jobject _______obj;
static int pfd[2];
static pthread_t logger;
static void *logger_thread() {
    JNIEnv *env;
    jstring str;
    (*_______jvm)->AttachCurrentThread(_______jvm,&env,NULL);
    ssize_t  rsize;
    char buf[2048];
    while((rsize = read(pfd[0], buf, sizeof(buf)-1)) > 0) {
        if(buf[rsize-1]=='\n') {
            rsize=rsize-1;
        }
        buf[rsize]=0x00;
        str = (*env)->NewStringUTF(env,buf);
        (*env)->CallStaticVoidMethod(env,_______obj,_______method, _______obj, str);
        (*env)->DeleteLocalRef(env,str);
    }
    (*env)->DeleteGlobalRef(env,_______method);
    (*env)->DeleteGlobalRef(env,_______obj);
    (*_______jvm)->DetachCurrentThread(_______jvm);
}
JNIEXPORT void JNICALL
Java_pojlib_util_JREUtils_logToLogger(JNIEnv *env, jclass clazz, jobject javaLogger) {
    // TODO: implement logToActivity()
    jclass loggableActivityClass = (*env)->FindClass(env,"pojlib/util/Logger");
    (*env)->GetJavaVM(env,&_______jvm);
    _______obj = (*env)->NewGlobalRef(env, javaLogger);

    setvbuf(stdout, 0, _IOLBF, 0); // make stdout line-buffered
    setvbuf(stderr, 0, _IONBF, 0); // make stderr unbuffered

    /* create the pipe and redirect stdout and stderr */
    pipe(pfd);
    dup2(pfd[1], 1);
    dup2(pfd[1], 2);

    /* spawn the logging thread */
    if(pthread_create(&logger, 0, logger_thread, 0) == -1) {
        jstring str = (*env)->NewStringUTF(env,"Failed to start logging!");
        (*env)->CallStaticVoidMethod(env,_______obj,_______method, _______obj, str);
        (*env)->DeleteLocalRef(env,str);
        (*env)->DeleteGlobalRef(env,_______method);
        (*env)->DeleteGlobalRef(env,_______obj);
    }
    pthread_detach(logger);

}