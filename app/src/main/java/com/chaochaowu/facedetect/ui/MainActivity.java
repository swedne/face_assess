package com.chaochaowu.facedetect.ui;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.chaochaowu.facedetect.adapter.FacesInfoAdapter;
import com.chaochaowu.facedetect.eventbus.FaceEvent;
import com.chaochaowu.facedetect.R;
import com.chaochaowu.facedetect.Utils;
import com.chaochaowu.facedetect.bean.FaceppBean;
import com.chaochaowu.facedetect.dagger.DaggerMainActivityComponent;
import com.chaochaowu.facedetect.dagger.MainPresenterModule;
import com.gc.materialdesign.views.ButtonRectangle;
import com.gc.materialdesign.views.ProgressBarCircularIndeterminate;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * 主界面
 *
 * @author chaochaowu
 */
public class MainActivity extends AppCompatActivity implements MainContract.View {

    private static final String TAG = "MainActivity";
    private static final int PERMISSIONS_REQUEST_CODE = 1;
    private static final int REQUEST_CODE_PICK_PHOTO = 2;
    private static final int CAMERA_REQUEST_CODE = 3;

    @BindView(R.id.imageView)
    ImageView imageView;
    @BindView(R.id.progressBar)
    ProgressBarCircularIndeterminate progressBar;
    @BindView(R.id.button)
    ButtonRectangle button;
    @BindView(R.id.photo)
    ButtonRectangle btnPhoto;
    @BindView(R.id.recyclerView)
    RecyclerView recyclerView;
    private int headerSize = 255;


    File mTmpFile;
    Uri imageUri;
    Bitmap photo = null;

    @Inject
    MainPresenter mPresenter;

    FacesInfoAdapter mAdapter;
    private List<FaceppBean.FacesBean> faces;
    private int imgWidth;
    private int imgHeight;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().hide();
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        DaggerMainActivityComponent.builder()
                .mainPresenterModule(new MainPresenterModule(this))
                .build()
                .inject(this);
        faces = new ArrayList<>();
        faces.add(new FaceppBean.FacesBean());
        mAdapter = new FacesInfoAdapter(this, faces, photo);
        mAdapter.setListener(new FacesInfoAdapter.onItemClickListener() {
            @Override
            public void onItemClicked(FaceppBean.FacesBean face, TextView tvBeauty) {
                gotoDetailActivity(face, tvBeauty);
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        recyclerView.setAdapter(mAdapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        imageView.post(new Runnable() {
            @Override
            public void run() {
                imgWidth = imageView.getWidth();
                //height is ready
                imgHeight = imageView.getHeight();
            }
        });
    }

    @OnClick(R.id.button)
    public void onButtonClicked() {
        takePhoto();
    }

    @OnClick(R.id.photo)
    public void onPhotoClicke() {
        pickPhoto();
    }

    protected void pickPhoto() {
        Intent intentGallery = new Intent(
                Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intentGallery, REQUEST_CODE_PICK_PHOTO);
    }


    private void gotoDetailActivity(FaceppBean.FacesBean face, TextView tvBeauty) {
        if (face.getAttributes() == null) {
            return;
        }
        Intent intent = new Intent(this, DetailActivity.class);
        android.support.v4.util.Pair<View, String> image = new android.support.v4.util.Pair(imageView, "image");
        android.support.v4.util.Pair<View, String> beauty = new android.support.v4.util.Pair(tvBeauty, "beauty");
        ActivityOptionsCompat optionsCompat =
                ActivityOptionsCompat.makeSceneTransitionAnimation(this, image, beauty);
        EventBus.getDefault().postSticky(new FaceEvent(photo, face));
        startActivity(intent, optionsCompat.toBundle());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.length > 0) {
                for (int grantResult : grantResults) {
                    if (grantResult == PackageManager.PERMISSION_DENIED) {
                        return;
                    }
                }
                takePhoto();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case CAMERA_REQUEST_CODE:
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 2;
                    photo = BitmapFactory.decodeFile(mTmpFile.getAbsolutePath(), options);
                    int bitmapDegree = Utils.getBitmapDegree(mTmpFile.getAbsolutePath());
                    if (bitmapDegree != 0) {
                        photo = Utils.rotateBitmapByDegree(this.photo, bitmapDegree);
                    }
                    displayPhoto(this.photo);
                    mAdapter.setPhoto(this.photo);
                    mPresenter.getDetectResultFromServer(this.photo);
                    break;
                case REQUEST_CODE_PICK_PHOTO:
                    doPhoto(data);
                    break;
                default:
                    break;
            }
        }
    }


    private void doPhoto(Intent data) {
        try {
            Uri uri = data.getData();
            if (uri == null) {
                Bundle bundle = data.getExtras();
                if (bundle != null) {
                    photo = (Bitmap) bundle.get("data");
                    // LogUtil.d("图片压缩前：" + getSize(photo) + "K 宽：" +
                    // photo.getWidth() + " 高：" + photo.getHeight());
                    int headWidth = photo.getWidth();
                    int headHeight = photo.getHeight();
                    if (photo.getWidth() > imgWidth) {
                        headWidth = imgWidth;
                    }
                    if (photo.getHeight() > imgHeight) {
                        headHeight = imgHeight;
                    }
                    photo = ThumbnailUtils.extractThumbnail(photo, headWidth, headHeight);
                    displayPhoto(photo);
                    mAdapter.setPhoto(photo);
                    mPresenter.getDetectResultFromServer(photo);
                    return;
                }
            } else {
                ContentResolver cr = getContentResolver();
                if (cr != null) {
                    photo = BitmapFactory.decodeStream(cr.openInputStream(uri));
                    int headWidth = photo.getWidth();
                    int headHeight = photo.getHeight();
                    if (headWidth > imageView.getWidth()) {
                        headWidth = imageView.getWidth();
                    }
                    if (headHeight > imageView.getHeight()) {
                        headHeight = imageView.getHeight();
                    }
                    photo = ThumbnailUtils.extractThumbnail(photo, headWidth, headHeight);
                    displayPhoto(photo);
                    mAdapter.setPhoto(photo);
                    mPresenter.getDetectResultFromServer(photo);
                    return;
                }
            }
        } catch (Exception ex) {
            Log.e("TAG", "选择图片失败");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private void takePhoto() {
        if (!Utils.checkAndRequestPermission(this, PERMISSIONS_REQUEST_CODE)) {
            return;
        }
        Intent intent = new Intent();
        intent.setAction(MediaStore.ACTION_IMAGE_CAPTURE);
        String path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/img";
        if (new File(path).exists()) {
            try {
                new File(path).createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        @SuppressLint("SimpleDateFormat")
        String filename = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        mTmpFile = new File(path, filename + ".jpg");
        mTmpFile.getParentFile().mkdirs();
        String authority = getPackageName() + ".provider";
        imageUri = FileProvider.getUriForFile(this, authority, mTmpFile);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
        startActivityForResult(intent, CAMERA_REQUEST_CODE);
    }

    @Override
    public void displayPhoto(Bitmap photo) {
        Glide.with(this).load(photo).into(imageView);
    }

    @Override
    public void displayFaceInfo(List<FaceppBean.FacesBean> faces) {
        this.faces.clear();
        if (faces == null) {
            this.faces.add(new FaceppBean.FacesBean());
            Toast.makeText(this, "未检测到面部信息", Toast.LENGTH_LONG).show();
        } else {
            this.faces.addAll(faces);
        }
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void showProgress() {
        button.setVisibility(View.INVISIBLE);
        btnPhoto.setVisibility(View.INVISIBLE);
        progressBar.setVisibility(View.VISIBLE);
    }

    @Override
    public void hideProgress() {
        button.setVisibility(View.VISIBLE);
        btnPhoto.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.GONE);
    }

}
