#include <jni.h>
#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

#include "log.h"

#include "utils.h"

#include <curl/curl.h>

typedef int (*Main_Function_t)(int, char**);
typedef void (*android_update_LD_LIBRARY_PATH_t)(char*);

char** convert_to_char_array(JNIEnv *env, jobjectArray jstringArray) {
	int num_rows = (*env)->GetArrayLength(env, jstringArray);
	char **cArray = (char **) malloc(num_rows * sizeof(char*));
	jstring row;
	
	for (int i = 0; i < num_rows; i++) {
		row = (jstring) (*env)->GetObjectArrayElement(env, jstringArray, i);
		cArray[i] = (char*)(*env)->GetStringUTFChars(env, row, 0);
    }
	
    return cArray;
}

void free_char_array(JNIEnv *env, jobjectArray jstringArray, const char **charArray) {
	int num_rows = (*env)->GetArrayLength(env, jstringArray);
	jstring row;
	
	for (int i = 0; i < num_rows; i++) {
		row = (jstring) (*env)->GetObjectArrayElement(env, jstringArray, i);
		(*env)->ReleaseStringUTFChars(env, row, charArray[i]);
	}
}

jstring convertStringJVM(JNIEnv* srcEnv, JNIEnv* dstEnv, jstring srcStr) {
	if (srcStr == NULL) {
		return NULL;
	}

	const char* srcStrC = (*srcEnv)->GetStringUTFChars(srcEnv, srcStr, 0);
	jstring dstStr = (*dstEnv)->NewStringUTF(dstEnv, srcStrC);
	(*srcEnv)->ReleaseStringUTFChars(srcEnv, srcStr, srcStrC);
	return dstStr;
}

JNIEXPORT jint JNICALL Java_android_os_OpenJDKNativeRegister_nativeRegisterNatives(JNIEnv *env, jclass clazz, jstring registerSymbol) {
	const char *register_symbol_c = (*env)->GetStringUTFChars(env, registerSymbol, 0);
	void *symbol = dlsym(RTLD_DEFAULT, register_symbol_c);
	if (symbol == NULL) {
		printf("dlsym %s failed: %s\n", register_symbol_c, dlerror());
		return -1;
	}
	
	int (*registerNativesForClass)(JNIEnv*) = symbol;
	int result = registerNativesForClass(env);
	(*env)->ReleaseStringUTFChars(env, registerSymbol, register_symbol_c);
	
	return (jint) result;
}

JNIEXPORT void JNICALL Java_pojlib_util_JREUtils_setLdLibraryPath(JNIEnv *env, jclass clazz, jstring ldLibraryPath) {
	android_update_LD_LIBRARY_PATH_t android_update_LD_LIBRARY_PATH;
	
	void *libdl_handle = dlopen("libdl.so", RTLD_LAZY);
	void *updateLdLibPath = dlsym(libdl_handle, "android_update_LD_LIBRARY_PATH");
	if (updateLdLibPath == NULL) {
		updateLdLibPath = dlsym(libdl_handle, "__loader_android_update_LD_LIBRARY_PATH");
		if (updateLdLibPath == NULL) {
			char *dl_error_c = dlerror();
			LOGE("Error getting symbol android_update_LD_LIBRARY_PATH: %s", dl_error_c);
		}
	}
	
	android_update_LD_LIBRARY_PATH = (android_update_LD_LIBRARY_PATH_t) updateLdLibPath;
	const char* ldLibPathUtf = (*env)->GetStringUTFChars(env, ldLibraryPath, 0);
	android_update_LD_LIBRARY_PATH(ldLibPathUtf);
	(*env)->ReleaseStringUTFChars(env, ldLibraryPath, ldLibPathUtf);
}

JNIEXPORT jboolean JNICALL Java_pojlib_util_JREUtils_dlopen(JNIEnv *env, jclass clazz, jstring name) {
	const char *nameUtf = (*env)->GetStringUTFChars(env, name, 0);
	void* handle = dlopen(nameUtf, RTLD_GLOBAL | RTLD_LAZY);
	if (!handle) {
		LOGE("dlopen %s failed: %s", nameUtf, dlerror());
	} else {
		LOGD("dlopen %s success", nameUtf);
	}
	(*env)->ReleaseStringUTFChars(env, name, nameUtf);
	return handle != NULL;
}

JNIEXPORT jint JNICALL Java_pojlib_util_JREUtils_chdir(JNIEnv *env, jclass clazz, jstring nameStr) {
	const char *name = (*env)->GetStringUTFChars(env, nameStr, NULL);
	int retval = chdir(name);
	(*env)->ReleaseStringUTFChars(env, nameStr, name);
	return retval;
}

JNIEXPORT jint JNICALL Java_pojlib_util_JREUtils_executeBinary(JNIEnv *env, jclass clazz, jobjectArray cmdArgs) {
	jclass exception_cls = (*env)->FindClass(env, "java/lang/UnsatisfiedLinkError");
	jstring execFile = (*env)->GetObjectArrayElement(env, cmdArgs, 0);
	
	char *exec_file_c = (char*) (*env)->GetStringUTFChars(env, execFile, 0);
	void *exec_binary_handle = dlopen(exec_file_c, RTLD_LAZY);
	
	(*env)->ReleaseStringUTFChars(env, execFile, exec_file_c);
	
	char *exec_error_c = dlerror();
	if (exec_error_c != NULL) {
		LOGE("Error: %s", exec_error_c);
		(*env)->ThrowNew(env, exception_cls, exec_error_c);
		return -1;
	}
	
	Main_Function_t Main_Function;
	Main_Function = (Main_Function_t) dlsym(exec_binary_handle, "main");
	
	exec_error_c = dlerror();
	if (exec_error_c != NULL) {
		LOGE("Error: %s", exec_error_c);
		(*env)->ThrowNew(env, exception_cls, exec_error_c);
		return -1;
	}
	
	int cmd_argv = (*env)->GetArrayLength(env, cmdArgs);
	char **cmd_args_c = convert_to_char_array(env, cmdArgs);
	int result = Main_Function(cmd_argv, cmd_args_c);
	free_char_array(env, cmdArgs, cmd_args_c);
	return result;
}

size_t curlCallback(char* contents, size_t size, size_t nmemb, void* user) {
	const char* path = user; // C allows that lol
	FILE* f = fopen(path, "wb");
	size_t result = fwrite(contents, size, nmemb, f);
	fclose(f);
	return result;
}

int downloadFile(const char* url, const char* filepath) {
	CURL* curl = curl_easy_init();

	curl_easy_setopt(curl, CURLOPT_URL, url);
	curl_easy_setopt(curl, CURLOPT_USERAGENT, "QuestCraft");
	curl_easy_setopt(curl, CURLOPT_SSL_VERIFYPEER, 0); // allows https lmfao
	curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, curlCallback);
	curl_easy_setopt(curl, CURLOPT_WRITEDATA, filepath);
	curl_easy_setopt(curl, CURLOPT_TIMEOUT_MS, 10000);
	curl_easy_setopt(curl, CURLOPT_CONNECTTIMEOUT_MS, 10000);
	curl_easy_setopt(curl, CURLOPT_ACCEPTTIMEOUT_MS, 10000);

	CURLcode response = curl_easy_perform(curl);

	const int MAX_RETRIES = 4;
	int retry = 0;

	while(response != CURLE_OK && retry++ < MAX_RETRIES)
		response = curl_easy_perform(curl);

	curl_easy_cleanup(curl);

	LOGI("utils.c:\n downloadFile\n (\n\t%s,\n\t%s\n ): response code: %d\n", url, filepath, (int)response);

	return (int)response;
}

JNIEXPORT jint JNICALL Java_pojlib_util_DownloadUtils_cDownloadFile(JNIEnv *env, jclass clazz, jstring url, jstring filepath) {
	const char* c_url = (*env)->GetStringUTFChars(env, url, NULL);
	const char* c_filepath = (*env)->GetStringUTFChars(env, filepath, NULL);

	int result = downloadFile(c_url, c_filepath);

	(*env)->ReleaseStringUTFChars(env, url, c_url);
	(*env)->ReleaseStringUTFChars(env, filepath, c_filepath);

	return result;
}

JNIEXPORT jstring JNICALL Java_pojlib_util_DownloadUtils_cResponseCodeString(JNIEnv *env, jclass clazz, jint code) {
	return (*env)->NewStringUTF(env, curl_easy_strerror((CURLcode)code));
}