package edu.buffalo.cse.cse486586.groupmessenger2;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.util.Log;

import org.json.JSONObject;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;

import static android.content.ContentValues.TAG;
import static android.content.Context.MODE_PRIVATE;

/**
 * GroupMessengerProvider is a key-value table. Once again, please note that we do not implement
 * full support for SQL as a usual ContentProvider does. We re-purpose ContentProvider's interface
 * to use it as a key-value table.
 *
 * Please read:
 *
 * http://developer.android.com/guide/topics/providers/content-providers.html
 * http://developer.android.com/reference/android/content/ContentProvider.html
 *
 * before you start to get yourself familiarized with ContentProvider.
 *
 * There are two methods you need to implement---insert() and query(). Others are optional and
 * will not be tested.
 *
 * @author stevko
 *
 */

public class GroupMessengerProvider extends ContentProvider {

    public String getJSONAsString() {

        int i;

        FileInputStream fileInputStream;
        StringBuilder sb = new StringBuilder();

        try {
            fileInputStream = getContext().openFileInput("myJSON.json");

            if (fileInputStream != null)
                while ((i = fileInputStream.read()) != -1)
                    sb.append((char) i);
        }

        catch (Exception e) {
            e.printStackTrace();
        }
        return sb.toString();
    }

    public void writeToJSON(JSONObject jsonObject) {

        try {
            FileOutputStream fileOutputStream = getContext().openFileOutput("myJSON.json", MODE_PRIVATE);
            if (fileOutputStream != null) {
                OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fileOutputStream);
                outputStreamWriter.write(jsonObject.toString());
                outputStreamWriter.flush();
                outputStreamWriter.close();
            }
        }
        catch (NullPointerException e) {
            e.printStackTrace();
        }
        catch (Exception e) {
            e.printStackTrace();
        }


    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        try {
            JSONObject jsonObject = new JSONObject(getJSONAsString());
            jsonObject.remove(selection);
            writeToJSON(jsonObject);
        }
        catch (Exception e){
            e.printStackTrace();
        }
        return 0;
    }

    @Override
    public String getType(Uri uri) {
        // You do not need to implement this.
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        /*
         * TODO: You need to implement this method. Note that values will have two columns (a key
         * column and a value column) and one row that contains the actual (key, value) pair to be
         * inserted.
         *
         * For actual storage, you can use any option. If you know how to use SQL, then you can use
         * SQLite. But this is not a requirement. You can use other storage options, such as the
         * internal storage option that we used in PA1. If you want to use that option, please
         * take a look at the code for PA1.
         */
        Log.v("insert", values.toString());

        try {
            JSONObject jsonObject = new JSONObject(getJSONAsString());
            jsonObject.put((String) values.get("key"), values.get("value"));
            writeToJSON(jsonObject);

        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return uri;
    }

    @Override
    public boolean onCreate() {
        // If you need to perform any one-time initialization task, please do it here.
        JSONObject jsonObject = new JSONObject();
        writeToJSON(jsonObject);

        return false;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        try {
            JSONObject jsonObject = new JSONObject(getJSONAsString());
            jsonObject.put(selection, values.get("value"));
            writeToJSON(jsonObject);
        }

        catch (Exception e){
            e.printStackTrace();
        }
        return 0;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        /*
         * TODO: You need to implement this method. Note that you need to return a Cursor object
         * with the right format. If the formatting is not correct, then it is not going to work.
         *
         * If you use SQLite, whatever is returned from SQLite is a Cursor object. However, you
         * still need to be careful because the formatting might still be incorrect.
         *
         * If you use a file storage option, then it is your job to build a Cursor * object. I
         * recommend building a MatrixCursor described at:
         * http://developer.android.com/reference/android/database/MatrixCursor.html
         */

        /*
        Referred from:
                https://stackoverflow.com/questions/20797323/how-to-create-cursor-from-jsonarray
         */


        MatrixCursor matrixCursor = new MatrixCursor(new String[] {"key", "value"});
        try {

            JSONObject jsonObject = new JSONObject(getJSONAsString());
            String val = (String) jsonObject.get(selection);
            Log.d(TAG, val + " - " + selection);
            Object [] row = {selection, val};
            matrixCursor.addRow(row);

        }
        catch (Exception e) {
            Log.e(TAG, "Not found");
        }


        Log.v("query", selection);
        return matrixCursor;
    }
}