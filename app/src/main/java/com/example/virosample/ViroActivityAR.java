/*
 * Copyright (c) 2017-present, Viro, Inc.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.virosample;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;

import com.viro.core.ARAnchor;
import com.viro.core.ARImageTarget;
import com.viro.core.ARNode;
import com.viro.core.ARScene;
import com.viro.core.AnimationTimingFunction;
import com.viro.core.AnimationTransaction;
import com.viro.core.AsyncObject3DListener;
import com.viro.core.ClickListener;
import com.viro.core.ClickState;
import com.viro.core.Material;
import com.viro.core.Node;
import com.viro.core.Object3D;
import com.viro.core.Sound;
import com.viro.core.Spotlight;
import com.viro.core.Surface;
import com.viro.core.Texture;
import com.viro.core.Vector;
import com.viro.core.ViroView;
import com.viro.core.ViroViewARCore;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * This activity demonstrates how to use an ARImageTarget. When a Tesla logo is
 * detected, a 3D Tesla car is created over the logo, along with controls that let
 * the user customize the car.
 */
public class ViroActivityAR extends Activity implements ARScene.Listener {
    private int numTaps = 0;
    private boolean turnedRight = false;
    private boolean turnedLeft = false;
    private static final String TAG = ViroActivityAR.class.getSimpleName();
    private ViroView mViroView;
    private ARScene mScene;
    private Node mDragonModelNode;
    private Node mColorChooserGroupNode;
    private Map<String, Pair<ARImageTarget, Node>> mTargetedNodesMap;


    // +---------------------------------------------------------------------------+
    //  Initialization
    // +---------------------------------------------------------------------------+

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mTargetedNodesMap = new HashMap<String, Pair<ARImageTarget, Node>>();
        mViroView = new ViroViewARCore(this, new ViroViewARCore.StartupListener() {
            @Override
            public void onSuccess() {
                onRenderCreate();
            }

            @Override
            public void onFailure(ViroViewARCore.StartupError error, String errorMessage) {
                Log.e(TAG, "Error initializing AR [" + errorMessage + "]");
            }
        });
        setContentView(mViroView);
    }

    /*
     Create the main ARScene. We add an ARImageTarget representing the Tesla logo to the scene,
     then we create a Node (teslaNode) that consists of the Tesla car and the controls to
     customize it. This Node is not yet added to the Scene -- we will wait until the Tesla logo
     is found.
     */
    private void onRenderCreate() {
        // Create the base ARScene
        mScene = new ARScene();
        mScene.setListener(this);
        mViroView.setScene(mScene);

        // Create an ARImageTarget for the Tesla logo
        Bitmap dragonLogoTargetBMP = getBitmapFromAssets("logo.png");
        ARImageTarget dragonTarget = new ARImageTarget(dragonLogoTargetBMP, ARImageTarget.Orientation.Up, 0.188f);
        mScene.addARImageTarget(dragonTarget);

        // Build the Dragon Node and add it to the Scene. Set it to invisible: it will be made
        // visible when the ARImageTarget is found.
        Node dragonNode = new Node();
        initDragonModel(dragonNode);
        //initColorPickerModels(dragonNode);
        initSceneLights(dragonNode);
        dragonNode.setVisible(false);
        mScene.getRootNode().addChildNode(dragonNode);

        // Link the Node with the ARImageTarget, such that when the image target is
        // found, we'll render the Node.
        linkTargetWithNode(dragonTarget, dragonNode);
    }

    /*
     Link the given ARImageTarget with the provided Node. When the ARImageTarget is
     found in the scene (by onAnchorFound below), the Node will be made visible and
     the target's transformations will be applied to the Node, thereby rendering the
     Node over the target.
     */
    private void linkTargetWithNode(ARImageTarget imageToDetect, Node nodeToRender){
        String key = imageToDetect.getId();
        mTargetedNodesMap.put(key, new Pair(imageToDetect, nodeToRender));
    }

    // +---------------------------------------------------------------------------+
    //  ARScene.Listener Implementation
    // +---------------------------------------------------------------------------+

    /*
     When an ARImageTarget is found, lookup the target's corresponding Node in the
     mTargetedNodesMap. Make the Node visible and apply the target's transformations
     to the Node. This makes the Node appear correctly over the target.

     (In this case, this makes the Tesla 3D model and color pickers appear directly
      over the detected Tesla logo)
     */
    @Override
    public void onAnchorFound(ARAnchor anchor, ARNode arNode) {
        String anchorId = anchor.getAnchorId();
        if (!mTargetedNodesMap.containsKey(anchorId)) {
            return;
        }

        Node imageTargetNode = mTargetedNodesMap.get(anchorId).second;
        Vector rot = new Vector(0,anchor.getRotation().y, 0);
        imageTargetNode.setPosition(anchor.getPosition());
        imageTargetNode.setRotation(rot);
        imageTargetNode.setVisible(true);
        animateDragonVisible(mDragonModelNode);

        AnimationTransaction.begin();
        AnimationTransaction.setAnimationDuration(300);
        mDragonModelNode.setPosition(new Vector(0,0,-0.19f));
        AnimationTransaction.commit();

        // Stop the node from moving in place once found
        ARImageTarget imgTarget = mTargetedNodesMap.get(anchorId).first;
        mScene.removeARImageTarget(imgTarget);
        mTargetedNodesMap.remove(anchorId);
    }

    @Override
    public void onAnchorRemoved(ARAnchor anchor, ARNode arNode) {
        String anchorId = anchor.getAnchorId();
        if (!mTargetedNodesMap.containsKey(anchorId)) {
            return;
        }

        Node imageTargetNode = mTargetedNodesMap.get(anchorId).second;
        imageTargetNode.setVisible(false);
    }

    @Override
    public void onAnchorUpdated(ARAnchor anchor, ARNode arNode) {
        // No-op
    }

    // +---------------------------------------------------------------------------+
    //  Scene Building Methods
    // +---------------------------------------------------------------------------+

    /*
     Init, loads the the Tesla Object3D, and attaches it to the passed in groupNode.
     */
    private void initDragonModel(Node groupNode) {
        // Creation of ObjectJni to the right
        Object3D objDragonNode = new Object3D();
        objDragonNode.setScale(new Vector(0.00f, 0.00f, 0.00f));
        objDragonNode.loadModel(mViroView.getViroContext(), Uri.parse("file:///android_asset/Toothless.obj"), Object3D.Type.OBJ, new AsyncObject3DListener() {
            @Override
            public void onObject3DLoaded(final Object3D object, final Object3D.Type type) {
                preloadDragonColorTextures(object);
            }

            @Override
            public void onObject3DFailed(final String error) {
                Log.e(TAG,"Dragon Model Failed to load.");
            }
        });

        groupNode.addChildNode(objDragonNode);
        mDragonModelNode = objDragonNode;

        // Set click listeners.
        mDragonModelNode.setClickListener(new ClickListener() {
            @Override
            public void onClick(int i, Node node, Vector vector) {

                numTaps++;
                if(numTaps == 4) {
                    AnimationTransaction.begin();
                    AnimationTransaction.setAnimationDuration(350);
                    node.setRotation(new Vector(0, 0, 0));
                    AnimationTransaction.commit();
                    numTaps = 0;
                }
                else if(numTaps == 3) {
                    Sound roar = new Sound(mViroView.getViroContext(), Uri.parse("file:///android_asset/roar.mp3"), null);
                    roar.setVolume(1.0f);
                    roar.setLoop(false);
                    roar.play();
                    AnimationTransaction.begin();
                    AnimationTransaction.setAnimationDuration(350);
                    node.setRotation(new Vector(-0.25f, 0, 0));
                    AnimationTransaction.commit();
                }
                else {
                    Sound steps = new Sound(mViroView.getViroContext(), Uri.parse("file:///android_asset/steps.mp3"), null);
                    steps.setVolume(1.0f);
                    steps.setLoop(false);
                    steps.play();
                    AnimationTransaction.begin();
                    AnimationTransaction.setAnimationDuration(700);
                    if (turnedLeft) {
                        node.setRotation(new Vector(0, -0.25f, 0));
                        turnedLeft = false;
                    } else if (turnedRight) {
                        node.setRotation(new Vector(0, 0.25f, 0));
                        turnedRight = false;
                    } else {
                        node.setRotation(new Vector(0, 0.25f, 0));
                        turnedLeft = true;
                    }
                    AnimationTransaction.commit();
                }

            }

            @Override
            public void onClickState(int i, Node node, ClickState clickState, Vector vector) {
                // No-op.
            }
        });
    }



    private void initSceneLights(Node groupNode){
        Node rootLightNode = new Node();

        // Construct a spot light for shadows
        Spotlight spotLight = new Spotlight();
        spotLight.setPosition(new Vector(0,5,0));
        spotLight.setColor(Color.parseColor("#FFFFFF"));
        spotLight.setDirection(new Vector(0,-1,0));
        spotLight.setIntensity(20);
        spotLight.setInnerAngle(1);
        spotLight.setOuterAngle(50);
        spotLight.setShadowMapSize(8192);
        spotLight.setShadowNearZ(2);
        spotLight.setShadowFarZ(7);
        spotLight.setShadowOpacity(.7f);
        spotLight.setCastsShadow(true);
        rootLightNode.addLight(spotLight);

        // Add our shadow planes.
        final Material material = new Material();
        material.setShadowMode(Material.ShadowMode.TRANSPARENT);
        Surface surface = new Surface(10,10);
        surface.setMaterials(Arrays.asList(material));
        Node surfaceShadowNode = new Node();
        surfaceShadowNode.setRotation(new Vector(Math.toRadians(-90), 0, 0));
        surfaceShadowNode.setGeometry(surface);
        surfaceShadowNode.setPosition(new Vector(0, 0, -0.7));
        rootLightNode.addChildNode(surfaceShadowNode);
        groupNode.addChildNode(rootLightNode);

        Texture environment = Texture.loadRadianceHDRTexture(Uri.parse("file:///android_asset/outdoor_env.hdr"));
        mScene.setLightingEnvironment(environment);
    }

    private Material preloadDragonColorTextures(Node node){
//        final Texture metallicTexture = new Texture(getBitmapFromAssets("object_car_main_Metallic.png"),
//                Texture.Format.RGBA8, true, true);
//        final Texture roughnessTexture = new Texture(getBitmapFromAssets("object_car_main_Roughness.png"),
//                Texture.Format.RGBA8, true, true);
//
//        Material material = new Material();
//        material.setMetalnessMap(metallicTexture);
//        material.setRoughnessMap(roughnessTexture);
//        material.setLightingModel(Material.LightingModel.PHYSICALLY_BASED);
//        node.getGeometry().setMaterials(Arrays.asList(material));
//
//        // Loop through color.
//        for (CAR_MODEL model : CAR_MODEL.values()) {
//            Bitmap carBitmap = getBitmapFromAssets(model.getCarSrc());
//            final Texture carTexture = new Texture(carBitmap, Texture.Format.RGBA8, true, true);
//            mCarColorTextures.put(model, carTexture);
//
//            // Preload our textures into the model
//            material.setDiffuseTexture(carTexture);
//        }
//
//        material.setDiffuseTexture(mCarColorTextures.get(CAR_MODEL.WHITE));
//        return material;
        final Bitmap dragonBitmap = getBitmapFromAssets("Toothless_Texture.png");
        Texture objectTexture = new Texture(dragonBitmap, Texture.Format.RGBA8, false, false);
        Material material = new Material();
        material.setDiffuseTexture(objectTexture);
        material.setLightingModel(Material.LightingModel.PHYSICALLY_BASED);
        node.getGeometry().setMaterials(Arrays.asList(material));
        return material;
    }

    // +---------------------------------------------------------------------------+
    //  Image Loading
    // +---------------------------------------------------------------------------+

    private Bitmap getBitmapFromAssets(final String assetName) {
        final InputStream istr;
        Bitmap bitmap = null;
        try {
            istr = getAssets().open(assetName);
            bitmap = BitmapFactory.decodeStream(istr);
        } catch (final IOException e) {
            throw new IllegalArgumentException("Loading bitmap failed!", e);
        }
        return bitmap;
    }

    // +---------------------------------------------------------------------------+
    //  Animation Utilities
    // +---------------------------------------------------------------------------+

    private void animateScale(Node node, long duration, Vector targetScale,
                              AnimationTimingFunction fcn, final Runnable runnable) {
        AnimationTransaction.begin();
        AnimationTransaction.setAnimationDuration(duration);
        AnimationTransaction.setTimingFunction(fcn);
        node.setScale(targetScale);
        if (runnable != null){
            AnimationTransaction.setListener(new AnimationTransaction.Listener() {
                @Override
                public void onFinish(AnimationTransaction animationTransaction) {
                    runnable.run();
                }
            });
        }
        AnimationTransaction.commit();
    }

//    private void animateColorPickerVisible(boolean isVisible, Node groupNode) {
//        if (isVisible){
//            animateScale(groupNode, 500, new Vector(1,1,1), AnimationTimingFunction.Bounce, null);
//        } else {
//            animateScale(groupNode, 200, new Vector(0,0,0), AnimationTimingFunction.Bounce, null);
//        }
//    }

    private void animateDragonVisible(Node dragon) {
        animateScale(dragon, 500, new Vector(0.09f, 0.09f, 0.09f), AnimationTimingFunction.EaseInEaseOut, null);
    }

    private void animateColorPickerClicked(final Node picker){
        animateScale(picker, 50, new Vector(0.8f, 0.8f, 0.8f), AnimationTimingFunction.EaseInEaseOut, new Runnable() {
            @Override
            public void run() {
                animateScale(picker, 50, new Vector(1,1,1), AnimationTimingFunction.EaseInEaseOut, null);
            }
        });
    }

    // +---------------------------------------------------------------------------+
    //  Lifecycle
    // +---------------------------------------------------------------------------+

    @Override
    protected void onStart() {
        super.onStart();
        mViroView.onActivityStarted(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mViroView.onActivityResumed(this);
    }

    @Override
    protected void onPause(){
        super.onPause();
        mViroView.onActivityPaused(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mViroView.onActivityStopped(this);
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        mViroView.onActivityDestroyed(this);
    }

    @Override
    public void onTrackingInitialized() {
        // No-op
    }

    @Override
    public void onTrackingUpdated(ARScene.TrackingState state, ARScene.TrackingStateReason reason) {
        // No-op
    }

    @Override
    public void onAmbientLightUpdate(float value, Vector v) {
        // No-op
    }
}