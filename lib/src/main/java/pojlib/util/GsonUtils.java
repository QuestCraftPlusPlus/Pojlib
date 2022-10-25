package pojlib.util;

import com.google.gson.Gson;

import java.io.FileNotFoundException;
import java.io.FileReader;

public class GsonUtils {

    public static <T> T jsonFileToObject(String path, Class<T> tClass) {
        try {
            return new Gson().fromJson(new FileReader(path), tClass);
        } catch (FileNotFoundException e) {
            return null;
        }
    }
}
