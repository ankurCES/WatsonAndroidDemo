package com.cesit.watsonanalyser;

import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.content.Intent;
import android.graphics.Bitmap;
import android.widget.ImageView;
import android.net.Uri;
import android.provider.MediaStore;
import android.content.pm.PackageManager;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import android.os.StrictMode;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import android.os.AsyncTask;
import android.app.ProgressDialog;
import android.os.Environment;
import java.text.SimpleDateFormat;
import java.util.Date;
import android.widget.LinearLayout.LayoutParams;

//Google Libs
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

//Watson imports
import com.ibm.watson.developer_cloud.visual_recognition.v3.VisualRecognition;
import com.ibm.watson.developer_cloud.visual_recognition.v3.model.ClassifyImagesOptions;
import com.ibm.watson.developer_cloud.visual_recognition.v3.model.DetectedFaces;
import com.ibm.watson.developer_cloud.visual_recognition.v3.model.Face;
import com.ibm.watson.developer_cloud.visual_recognition.v3.model.ImageClassification;
import com.ibm.watson.developer_cloud.visual_recognition.v3.model.ImageFace;
import com.ibm.watson.developer_cloud.visual_recognition.v3.model.ImageText;
import com.ibm.watson.developer_cloud.visual_recognition.v3.model.RecognizedText;
import com.ibm.watson.developer_cloud.visual_recognition.v3.model.VisualClassification;
import com.ibm.watson.developer_cloud.visual_recognition.v3.model.VisualClassifier;
import com.ibm.watson.developer_cloud.visual_recognition.v3.model.VisualRecognitionOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private FloatingActionButton fab;

    private ImageView imageView;

    private TextView tv;

    private int PICK_IMAGE_REQUEST = 100;

    private String picturePath;
    private Uri fileUri;
    private Uri selectedImage;
    private Bitmap photo;
    private LinearLayout watsonResultLayout;

    boolean facesPresent =  false;
    boolean classesPresent = false;
    boolean scenesPresent = false;

    JSONArray parsedFaceList = new JSONArray();
    JSONArray parsedClassList = new JSONArray();

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    //Watson Code

    VisualRecognition service;
    ClassifyImagesOptions options;
    VisualRecognitionOptions vrOptions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT > 9) {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }

        service = new VisualRecognition(VisualRecognition.VERSION_DATE_2016_05_20);
        service.setApiKey("<your_api_key_here>");


        imageView = (ImageView) findViewById(R.id.imageView);
        tv = (TextView) findViewById(R.id.result);

        fab = (FloatingActionButton) findViewById(R.id.fab);

        fab.setOnClickListener(this);
    }

    private void showFileChooser() {
        // Check Camera
        if (getApplicationContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_CAMERA)) {
            // Open default camera
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);

            // start the image capture Intent
            startActivityForResult(intent, 100);

        } else {
            Toast.makeText(getApplication(), "Camera not supported", Toast.LENGTH_LONG).show();
        }

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        fileUri = Uri.fromFile(getOutputMediaFile());
        intent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);
        startActivityForResult(intent, 100);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 100 && resultCode == RESULT_OK) {
            imageView.setImageURI(fileUri);

            File f = new File(fileUri.getPath());
            vrOptions = new VisualRecognitionOptions.Builder().images(f).build();
            options = new ClassifyImagesOptions.Builder().images(f).build();


            GetWatsonResponse task = new GetWatsonResponse(this);
            task.execute();
        }
    }

    private static File getOutputMediaFile(){
        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "CameraDemo");

        if (!mediaStorageDir.exists()){
            if (!mediaStorageDir.mkdirs()){
                return null;
            }
        }

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        return new File(mediaStorageDir.getPath() + File.separator +
                "IMG_"+ timeStamp + ".jpg");
    }

    @Override
    public void onClick(View v) {

        if (v == fab) {
            showFileChooser();
        }
    }

    private Result analyze(VisualRecognitionOptions options, ClassifyImagesOptions classifyOptions) {
        Result result = new Result();
        Log.d("Watson", "Face detection");
        try {
            DetectedFaces execute = service.detectFaces(options).execute();
            List<ImageFace> imageFaces = execute.getImages();
            if (!imageFaces.isEmpty()) {
                result.faces = imageFaces.get(0).getFaces();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        Log.d("Watson", "Image Classification");
        try {
            VisualClassification execute = service.classify(classifyOptions).execute();
            List<ImageClassification> imageClassifiers = execute.getImages();
            if (!imageClassifiers.isEmpty()) {
                result.keywords = imageClassifiers.get(0).getClassifiers();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        Log.d("Watson", "Scene Recognition");
        try {
            RecognizedText execute = service.recognizeText(options).execute();
            List<ImageText> imageTexts = execute.getImages();
            if (!imageTexts.isEmpty()) {
                result.sceneText = imageTexts.get(0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

    private void ParseFaceDataWatsonResponse (JSONArray faceList) throws JSONException {

        for(int i=0; i<faceList.length(); i++){

            JSONObject parsedFace = new JSONObject();

            JSONObject face = (JSONObject)faceList.get(i);

            JSONObject age = face.getJSONObject("age");
            JSONObject gender = face.getJSONObject("gender");
            JSONObject identity = null;

            if(face.has("identity")){
                identity = face.getJSONObject("identity");

                parsedFace.put("Celebrity", identity.getString("name"));
                parsedFace.put("MatchProb", identity.getString("score"));
            }

            String ageString = age.getString("min");

            if(age.has("max")){
                ageString = ageString + " - " + age.get("max");
            }


            String genderString = gender.getString("gender");

            float ageScore = Float.parseFloat(age.getString("score"));
            float genderScore = Float.parseFloat(gender.getString("score"));

            parsedFace.put("Age", ageString);
            parsedFace.put("Gender", genderString);
            parsedFace.put("AgeScore", ageScore);
            parsedFace.put("GenderScore", genderScore);

            parsedFaceList.put(parsedFace);
        }

    }

    private void ParseClassificationDataWatsonResponse (JSONArray keywordList) throws JSONException {

        for(int i=0; i<keywordList.length(); i++){
            JSONObject classObj = (JSONObject)keywordList.get(i);
            parsedClassList = classObj.getJSONArray("classes");
        }
    }

    private void updateView(String key, int value){

        ProgressBar pb = new ProgressBar(getApplicationContext(), null, android.R.attr.progressBarStyleHorizontal);

        LayoutParams lp = new LayoutParams(
                LayoutParams.MATCH_PARENT, // Width in pixels
                40 // Height of progress bar
        );

        pb.setLayoutParams(lp);
        pb.getProgressDrawable().setColorFilter(Color.rgb(219, 70, 57), PorterDuff.Mode.SRC_IN);

        pb.setProgress(value);

        TextView valueTV = new TextView(this);
        valueTV.setText(key);
        valueTV.setTextColor(Color.rgb(114, 13, 13));
        valueTV.setTextSize(20);
        valueTV.setLayoutParams(new LayoutParams(
                LayoutParams.FILL_PARENT,
                LayoutParams.WRAP_CONTENT));
        watsonResultLayout.addView(valueTV);
        watsonResultLayout.addView(pb);
    }

    private class GetWatsonResponse extends AsyncTask<String, Void, String> {
        private ProgressDialog dialog;

        public GetWatsonResponse(MainActivity activity) {
            dialog = new ProgressDialog(activity);
        }


        @Override
        protected void onPreExecute() {
            dialog.setMessage("Watson is analysing...");
            dialog.show();
        }

        @Override
        protected String doInBackground(String... params) {
            Result res = analyze(vrOptions, options);
            Log.d("RESULT", gson.toJson(res));
            try {
                JSONObject rawWatsonData = new JSONObject(gson.toJson(res));

                JSONArray faceList = rawWatsonData.getJSONArray("faces");
                JSONArray classList = rawWatsonData.getJSONArray("keywords");

                if(faceList.length() > 0){
                    facesPresent = true;
                    ParseFaceDataWatsonResponse(faceList);
                }

                if(classList.length() > 0){
                    classesPresent = true;
                    ParseClassificationDataWatsonResponse(classList);
                }

                //JSONArray sceneList = rawWatsonData.getJSONArray("faces");


                return null;
            } catch (JSONException e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(String result) {
            dialog.dismiss();

            watsonResultLayout = (LinearLayout) findViewById(R.id.resultLayout);

            if(classesPresent){
                TextView valueTV = new TextView(getBaseContext());
                valueTV.setText("Classification ");
                valueTV.setTextColor(Color.BLACK);
                valueTV.setTextSize(25);
                valueTV.setLayoutParams(new LayoutParams(
                        LayoutParams.FILL_PARENT,
                        LayoutParams.WRAP_CONTENT));
                watsonResultLayout.addView(valueTV);

                for(int i=0 ; i < parsedClassList.length(); i++){


                    try {
                        JSONObject tempObj = parsedClassList.getJSONObject(i);

                        int temperature = (int) (Float.parseFloat(tempObj.getString("score")) * 100);

                        updateView("Class : "+tempObj.getString("class")+"\n Probability : "+tempObj.getString("score"),temperature );


                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                }
            }

            if(facesPresent){

                for(int i=0 ; i < parsedFaceList.length(); i++){
                    TextView valueTV = new TextView(getBaseContext());
                    valueTV.setText("Face "+ (i+1));
                    valueTV.setTextColor(Color.BLACK);
                    valueTV.setTextSize(25);
                    valueTV.setLayoutParams(new LayoutParams(
                            LayoutParams.FILL_PARENT,
                            LayoutParams.WRAP_CONTENT));
                    watsonResultLayout.addView(valueTV);

                    try {
                        JSONObject tempObj = parsedFaceList.getJSONObject(i);

                        if(tempObj.has("Celebrity")){
                            int scoreTemp = (int) (Float.parseFloat(tempObj.getString("MatchProb")) * 100);
                            updateView("Celebrity Match : "+tempObj.getString("Celebrity")+"\n Probability : "+tempObj.getString("MatchProb"),scoreTemp );
                        }

                        int ageTemperature = (int) (Float.parseFloat(tempObj.getString("AgeScore")) * 100);
                        int  genderTemperature= (int) (Float.parseFloat(tempObj.getString("GenderScore"))*100);

                        updateView("Age : "+tempObj.getString("Age")+"\n Probability : "+tempObj.getString("AgeScore"),ageTemperature );

                        updateView("Gender : "+tempObj.getString("Gender")+"\n Probability : "+tempObj.getString("GenderScore"),genderTemperature );

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                }

            }



        }
    }

    @SuppressWarnings("unused")
    public static class Result {
        String url;
        List<Face> faces;
        List<VisualClassifier> keywords;
        ImageText sceneText;
    }
}
