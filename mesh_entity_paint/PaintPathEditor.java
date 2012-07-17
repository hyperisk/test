package hyperden.mesh_entity_paint;

import hyperden.heli_one.Config;
import hyperden.heli_one.app.MainActivityBase;
import hyperden.heli_one.renderer.Camera;
import hyperden.heli_one.renderer.GameViewWidgetWrapper;
import hyperden.heli_one.renderer.RendererManager;
import hyperden.heli_one.renderer.RendererManager.FrameRendererInterface;
import hyperden.heli_one.renderer.StatRenderer;
import hyperden.util.Logger;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;

public class PaintPathEditor extends Handler {
    private Context context_;
    private RendererManager rendererManager_;
    private GameViewWidgetWrapper gameViewWidgetWrapper_;
    private PaintPathMeshes meshes_;
    private PaintPathEditorUtil util_;
    private static String meshNameEditing_ = new String();
    private String submeshNameEditing_;
    private PaintPathEntity entityEditing_;
    private int elementIndexEditing_;

    private Path originalCursorPath_;
    private Path transformedCursorPath_;
    private int cursorPosDeciX_;
    private int cursorPosDeciY_;
    private final static int CURSOR_RADIUS = 8;
    private long lastCursorBlinkTime_;
    private int cursorColorIndex_;
    private int cursorColorArray_[];
    private int cursorColorBlinkIndex_;
    private FrameRenderer frameRenderer_;
    private boolean frameRenderLock_;

    public enum MeshElemEditMode {
        MOVE_ELEM,
        MOVE_SUBMESH,
        MOVE_MESH,
        SCALE_SUBMESH,
        SCALE_MESH,
        DUP_AS_IS,
        DUP_HORZ_MIRROR,
        DUP_VERT_MIRROR,
        EFFECT,
        ANIM_KEY,
        ANIM_TRACK,
    }

    private MeshElemEditMode meshElemEditMode_;

    private String animKeyNameEditing_;
    private int animKeyPercent_;
    private String animTrackNameEditing_;
    private float animTrackTimePos_;
    private boolean animTrackPlaying_;
    private String effectNameEditing_;
    private int effectPercent_;

    public enum TransformMode {
        TRANSLATION,
        ROTATION,
        SCALE,
        __CURSOR_POS,
    };

    private TransformMode transformMode_;

    private class FrameRenderer implements FrameRendererInterface {
        private Camera camera_;

        public FrameRenderer(Camera camera) {
            camera_ = camera;
        }

        public void renderFrame(Canvas canvas, Paint paint) {
            if (frameRenderLock_) {
                return;
            }
            camera_.adjustZoomToShowHeliAndGround(2);
            canvas.drawColor(Color.BLACK);
            if (camera_.cameraState_.posX_ != 0) {
                camera_.cameraState_.moveToAbsPos(0);
            }
            
            drawEntity(canvas, paint, camera_);
            drawCursor(canvas, paint, camera_);
            setMenuMsg();
        }
    }

    public void onFrameUpdate(int frameLengthMsec) {
        if (animTrackPlaying_ && (animTrackNameEditing_.length() > 0)) {
            PaintPathMesh mesh = meshes_.getMesh(meshNameEditing_);
            PaintPathMesh.AnimTrack animTrack = mesh
                    .getAnimTrack(animTrackNameEditing_);
            animTrackTimePos_ += (float) frameLengthMsec / 1000.f;
            if (animTrackTimePos_ >= animTrack.getTimeLengh()) {
                animTrackTimePos_ = 0.f;
            }

            // FPS drops from 30 to 20
            // Message handlerMsg = obtainMessage();
            // Bundle b = new Bundle();
            // b.putString("type",
            // EventType.CALL_updateDialogScrollbarText.toString());
            // handlerMsg.setData(b);
            // sendMessage(handlerMsg);

            entityEditing_.applyAnimTrackFromMesh(animTrackNameEditing_,
                    animTrackTimePos_, true);
        }
    }

    public PaintPathEditor(Context context, RendererManager rendererManager,
            GameViewWidgetWrapper gameViewWidgetWrapper) {
        context_ = context;
        rendererManager_ = rendererManager;
        gameViewWidgetWrapper_ = gameViewWidgetWrapper;
        meshes_ = null;
        submeshNameEditing_ = new String();
        cursorColorBlinkIndex_ = 0;
        frameRenderer_ = new FrameRenderer(rendererManager.getCamera());
        meshElemEditMode_ = MeshElemEditMode.MOVE_ELEM;
        animKeyNameEditing_ = new String();
        transformMode_ = TransformMode.TRANSLATION;
        animTrackNameEditing_ = new String();
        effectNameEditing_ = new String();
        effectPercent_ = 0;
        frameRenderLock_ = false;

        onDialogScrollbarSeekBarChangedHandler_ = new OnDialogScrollbarSeekBarChangedHandler();
        MainActivityBase.dialog_scrollbar_SeekBar_.setOnSeekBarChangeListener(
                onDialogScrollbarSeekBarChangedHandler_);
    }

    public void startEditing() {
        originalCursorPath_ = new Path();
        originalCursorPath_.moveTo(0, -CURSOR_RADIUS);
        originalCursorPath_.lineTo(0, CURSOR_RADIUS);
        originalCursorPath_.moveTo(-CURSOR_RADIUS, 0);
        originalCursorPath_.lineTo(CURSOR_RADIUS, 0);

        transformedCursorPath_ = new Path();
        lastCursorBlinkTime_ = SystemClock.elapsedRealtime();
        cursorColorIndex_ = 0;
        cursorColorArray_ = new int[8];
        cursorColorArray_[0] = Color.argb(150, 200, 200, 180); // on element
        cursorColorArray_[1] = Color.argb(150, 150, 50, 40); // out of element
        cursorColorArray_[2] = Color.argb(200, 50, 150, 40); // out of submesh
        cursorColorArray_[3] = Color.argb(200, 50, 50, 140); // out of mesh
        cursorColorArray_[4] = Color.argb(50, 50, 250, 40); // scale
        cursorColorArray_[5] = Color.argb(100, 50, 50, 240); // dup
        cursorColorArray_[6] = Color.argb(200, 150, 100, 150); // anim key
        cursorColorArray_[7] = Color.argb(10, 50, 50, 50); // anim track

        meshes_ = rendererManager_.meshes_;
        if (meshes_.getNumMeshes() > 0) {
            if (meshNameEditing_.length() == 0) {
                meshNameEditing_ = meshes_.getFirstMeshName();
            }
            PaintPathMesh mesh = meshes_.getMesh(meshNameEditing_);
            if (mesh.getNumSubmeshes() > 0) {
                submeshNameEditing_ = mesh.getFirstSubmeshName();
                moveCursorToElement();
            }
        }

        rendererManager_.setCustomFrameRenderer(frameRenderer_);
        util_ = new PaintPathEditorUtil(meshes_);
        gameViewWidgetWrapper_.clearTutorialMsg();

        Config.getSingleton().maxZoomPercent_ = Config.getSingleton().MAX_ZOOM_PERCENT_MESH_EDIT;
        Config.getSingleton().runningPaintPathEditor_ = true;
    }

    public String finishEditing() {
        // note: called from the thread in SimThread
        Logger.logInfo("finishEditing, num meshes: " + meshes_.getNumMeshes());
        FileOutputStream fos;
        String errorMsg = "";
        try {
            fos = context_.openFileOutput("meshes_anim_data", Context.MODE_PRIVATE);
            meshes_.logAllMeshNames();
            boolean success = meshes_.serialize(fos);
            if (!success) {
            	errorMsg = "serialization failed";
            }
            fos.close();
            Logger.logInfo("serialization success");
        } catch (FileNotFoundException e) {
        	errorMsg = "FileNotFoundException";
            e.printStackTrace();
        } catch (IOException e) {
        	errorMsg = "IOException";
            e.printStackTrace();
        }

        gameViewWidgetWrapper_.setDialogScrollBarViz(View.INVISIBLE);

        rendererManager_.setCustomFrameRenderer(null);
        Config.getSingleton().runningPaintPathEditor_ = false;
        return errorMsg;
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_RIGHT_BRACKET) {
            if (MeshElemEditMode.ANIM_KEY == meshElemEditMode_) {
                resetCurrentEntityAnim();
                selectNextSubmesh();
                selectFirstAnimKey();
            } else if (MeshElemEditMode.ANIM_TRACK == meshElemEditMode_) {
                // nop
            } else if (event.isShiftPressed()) {
                selectNextMesh();
            } else {
                selectNextSubmesh();
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_LEFT_BRACKET) {
            if (MeshElemEditMode.ANIM_KEY == meshElemEditMode_) {
                resetCurrentEntityAnim();
                selectPreviousSubmesh();
                selectFirstAnimKey();
            } else if (MeshElemEditMode.ANIM_TRACK == meshElemEditMode_) {
                // nop
            } else if (event.isShiftPressed()) {
                selectPreviousMesh();
            } else {
                selectPreviousSubmesh();
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_EQUALS) {
            if (MeshElemEditMode.ANIM_KEY == meshElemEditMode_) {
                selectNextAnimKey();
            } else if (MeshElemEditMode.ANIM_TRACK == meshElemEditMode_) {
                selectNextAnimTrack();
            } else if (MeshElemEditMode.EFFECT == meshElemEditMode_) {
                selectNextEffect();
            } else {
                selectNextElement();
            }
        } else if (keyCode == KeyEvent.KEYCODE_MINUS) {
            if (MeshElemEditMode.ANIM_KEY == meshElemEditMode_) {
                selectPreviousAnimKey();
            } else if (MeshElemEditMode.ANIM_TRACK == meshElemEditMode_) {
                selectPreviousAnimTrack();
            } else {
                selectPreviousElement();
            }
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (submeshNameEditing_.length() > 0) {
                moveCursorOrElem(-10, 0, event);
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (submeshNameEditing_.length() > 0) {
                moveCursorOrElem(10, 0, event);
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (submeshNameEditing_.length() > 0) {
                moveCursorOrElem(0, 10, event);
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (submeshNameEditing_.length() > 0) {
                moveCursorOrElem(0, -10, event);
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_A) {
            if ((meshNameEditing_.length() == 0)
                    || (submeshNameEditing_.length() == 0)) {
                return true;
            }
            if (!event.isShiftPressed()
                    && (MeshElemEditMode.ANIM_KEY == meshElemEditMode_)) {
                util_.addNewAnimKey(meshNameEditing_, submeshNameEditing_, context_, this);
            }
        } else if (keyCode == KeyEvent.KEYCODE_C) {
            if (0 == meshNameEditing_.length()) {
                if (event.isShiftPressed()) {
                	util_.cloneMesh(context_, this);
                }
                return true;
            }
            if (MeshElemEditMode.ANIM_TRACK == meshElemEditMode_) {
                return true;
            }
            if (!event.isShiftPressed()) {
                if (MeshElemEditMode.ANIM_KEY == meshElemEditMode_) {
                    transformMode_ = TransformMode.__CURSOR_POS;
                } else if (elementIndexEditing_ >= 0) {
                    util_.setColorEditingSubmesh(context_, meshNameEditing_,
                            submeshNameEditing_);
                }
            }
        } else if (keyCode == KeyEvent.KEYCODE_D) {
            util_.selectNextOrPrevMode(context_, event.isShiftPressed(), this);
        } else if (keyCode == KeyEvent.KEYCODE_I) {
            if ((meshNameEditing_.length() == 0)
                    || (submeshNameEditing_.length() == 0)) {
                return true;
            }
            if (!event.isShiftPressed()) {
                if (MeshElemEditMode.ANIM_KEY == meshElemEditMode_) {
                    resetAnimTransform();
                } else if (MeshElemEditMode.ANIM_TRACK == meshElemEditMode_) {
                    // nop
                } else if (elementIndexEditing_ > 0) {
                    PaintPathMesh mesh = meshes_.getMesh(meshNameEditing_);
                    PaintPathSubmesh submesh = mesh.getSubmesh(submeshNameEditing_);
                    boolean success = submesh.insertDupElement(elementIndexEditing_);
                    if (success) {
                        StatRenderer.setMiscStatMsg("pathEditor",
                                "inserted element " + elementIndexEditing_);
                        moveCursorToElement();
                    } else {
                        StatRenderer.setMiscStatMsg("pathEditor",
                                "Error insert element " + elementIndexEditing_);
                    }
                }
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_L) {
            if ((meshNameEditing_.length() > 0)
                    && (submeshNameEditing_.length() > 0) &&
                    !meshElemEditMode_.toString().startsWith("ANIM")) {
                PaintPathMesh mesh = meshes_.getMesh(meshNameEditing_);
                PaintPathSubmesh submesh = mesh.getSubmesh(submeshNameEditing_);
                if (submesh.getNumElements() == 0) {
                    StatRenderer.setMiscStatMsg("pathEditor",
                            "ERROR: start first element with MOVE");
                    return true;
                }
                // else if (submesh.hasAnyElementAtPos(cursorPosDeciX_,
                // cursorPosDeciY_, 3)) {
                // StatRenderer.setMiscStatMsg("pathEditor",
                // "ERROR: element already exists at the position");
                // return true;
                // }

                int numElements = submesh.appendElement(
                        PaintPathElement.PathType.LINE_TO, cursorPosDeciX_,
                        cursorPosDeciY_);
                StatRenderer.setMiscStatMsg("pathEditor",
                        "added LINE, num element: " + numElements);
                if (entityEditing_ != null) {
                    entityEditing_.populateAllSubentities();
                }
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_M) {
            if ((meshNameEditing_.length() > 0)
                    && (submeshNameEditing_.length() > 0) &&
                    !meshElemEditMode_.toString().startsWith("ANIM")) {
                PaintPathMesh mesh = meshes_.getMesh(meshNameEditing_);
                PaintPathSubmesh submesh = mesh.getSubmesh(submeshNameEditing_);
                if (submesh.getNumElements() > 0) {
                    StatRenderer.setMiscStatMsg("pathEditor",
                            "ERROR: only the first elem can be MOVE");
                    return true;
                }
                int numElements = submesh.appendElement(
                        PaintPathElement.PathType.MOVE_TO, cursorPosDeciX_,
                        cursorPosDeciY_);
                StatRenderer.setMiscStatMsg("pathEditor",
                        "added MOVE, num element: " + numElements);
                if (entityEditing_ != null) {
                    entityEditing_.populateAllSubentities();
                }
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_N) {
            if (0 == meshNameEditing_.length()) {
                if (event.isShiftPressed()) {
                    this.createNewMesh();
                }
            } else if (0 == submeshNameEditing_.length()) {
                if (event.isShiftPressed()) {
                    this.createNewSubmesh();
                } else {
                    util_.renameEditingMesh(context_, meshNameEditing_, submeshNameEditing_, this);
                }
            } else if (!event.isShiftPressed()) {
                if (MeshElemEditMode.ANIM_KEY == meshElemEditMode_) {
                    if (animKeyNameEditing_.length() > 0) {
                        util_.renameEditingAnimKey(meshNameEditing_, submeshNameEditing_,
                                context_, animKeyNameEditing_, this);
                    }
                } else if (MeshElemEditMode.ANIM_TRACK == meshElemEditMode_) {
                    // nop
                } else {
                    util_.renameEditingSubmesh(context_, meshNameEditing_, submeshNameEditing_, this);
                }
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_P) {
            if ((meshNameEditing_.length() == 0) || (submeshNameEditing_.length() == 0)) {
                return true;
            }
            if ((MeshElemEditMode.ANIM_TRACK == meshElemEditMode_)
                    && (animTrackNameEditing_.length() > 0)) {
                animTrackPlaying_ = !animTrackPlaying_;
            }
        } else if (keyCode == KeyEvent.KEYCODE_R) {
            if ((meshNameEditing_.length() == 0) && (submeshNameEditing_.length() == 0)) {
                return true;
            }
            if (event.isShiftPressed()) {
                if ((MeshElemEditMode.ANIM_KEY == meshElemEditMode_) && (animKeyNameEditing_.length() > 0)) {
                    removeEditingAnimKey();
                } else if (MeshElemEditMode.ANIM_TRACK == meshElemEditMode_) {
                    // nop
                } else if ((submeshNameEditing_.length() == 0)
                        && (meshNameEditing_.length() > 0)) {
                    removeEditingMesh();
                } else if (elementIndexEditing_ == -1) {
                    removeEditingSubmesh();
                }
                if (elementIndexEditing_ > 1) {
                    removeEditingElem();
                }
            } else {
                if (!event.isShiftPressed()
                        && (MeshElemEditMode.ANIM_KEY == meshElemEditMode_)) {
                    transformMode_ = TransformMode.ROTATION;
                }
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_S) {
            if (!event.isShiftPressed()
                    && (MeshElemEditMode.ANIM_KEY == meshElemEditMode_)) {
                transformMode_ = TransformMode.SCALE;
            }
        } else if (keyCode == KeyEvent.KEYCODE_T) {
            if (!event.isShiftPressed()
                    && (MeshElemEditMode.ANIM_KEY == meshElemEditMode_)) {
                transformMode_ = TransformMode.TRANSLATION;
            }
        }

        return false;
    }

    private void moveCursorOrElem(int diffDeciX, int diffDeciY, KeyEvent event) {
        if ((event.getMetaState() & KeyEvent.META_SHIFT_LEFT_ON) > 0) {
            diffDeciX *= 10;
            diffDeciY *= 10;
        } else if ((event.getMetaState() & KeyEvent.META_SHIFT_RIGHT_ON) > 0) {
            diffDeciX /= 10;
            diffDeciY /= 10;
        }

        if ((meshNameEditing_.length() == 0)
                || (submeshNameEditing_.length() == 0)) {
            return;
        }
        if (MeshElemEditMode.ANIM_TRACK == meshElemEditMode_) {
            return;
        }
        if (MeshElemEditMode.EFFECT == meshElemEditMode_) {
            return;
        }
        util_.moveMeshElem(meshElemEditMode_, elementIndexEditing_,
                meshNameEditing_, submeshNameEditing_, animKeyNameEditing_,
                entityEditing_,
                diffDeciX, diffDeciY, transformMode_,
                (float) cursorPosDeciX_ / 10.f, (float) cursorPosDeciY_ / 10.f,
                animKeyPercent_, this);
        if (!meshElemEditMode_.toString().startsWith("ANIM") ||
                (meshElemEditMode_.toString().startsWith("ANIM") &&
                (transformMode_ == TransformMode.__CURSOR_POS))) {
            cursorPosDeciX_ += diffDeciX;
            cursorPosDeciY_ += diffDeciY;
        }
    }

    private void createNewMesh() {
        meshNameEditing_ = new String("unnamed_mesh_") + meshes_.getNumMeshes();
        StatRenderer.setMiscStatMsg("pathEditor", "start new mesh "
                + meshNameEditing_);
        meshes_.addEmptyMesh(meshNameEditing_);
        submeshNameEditing_ = new String();
    }

    private void createNewSubmesh() {
        PaintPathMesh mesh = meshes_.getMesh(meshNameEditing_);
        submeshNameEditing_ = new String("unnamed_submesh_")
                + mesh.getNumSubmeshes();
        StatRenderer.setMiscStatMsg("pathEditor", "start new submesh "
                + submeshNameEditing_);
        mesh.addEmptySubmesh(submeshNameEditing_, Color.WHITE);
        elementIndexEditing_ = -1;
    }

    private void selectNextMesh() {
        if (0 == meshNameEditing_.length()) {
            if (meshes_.getNumMeshes() > 0) {
                meshNameEditing_ = meshes_.getFirstMeshName();
            }
        } else {
            meshNameEditing_ = meshes_.getNextMeshName(meshNameEditing_);
        }
        onEditingMeshChanged();
    }

    private void selectPreviousMesh() {
        if (0 == meshNameEditing_.length()) {
            if (meshes_.getNumMeshes() > 0) {
                meshNameEditing_ = meshes_.getLastMeshName();
            }
        } else {
            meshNameEditing_ = meshes_.getPreviousMeshName(meshNameEditing_);
        }
        onEditingMeshChanged();
    }

    private void onEditingMeshChanged() {
        if (meshNameEditing_.length() > 0) {
            PaintPathMesh mesh = meshes_.getMesh(meshNameEditing_);
            if (mesh.getNumSubmeshes() == 0) {
                submeshNameEditing_ = new String();
            } else {
                submeshNameEditing_ = mesh.getFirstSubmeshName();
                PaintPathSubmesh submesh = mesh.getSubmesh(submeshNameEditing_);
                if (submesh.getNumElements() > 0) {
                    elementIndexEditing_ = 0;
                    moveCursorToElement();
                } else {
                    elementIndexEditing_ = -1;
                }
            }
        }
        entityEditing_ = null;
    }

    private void selectNextSubmesh() {
        if (!canChangeEditingSubmesh()) {
            return;
        }
        String submeshNameBefore = null;
        PaintPathMesh mesh = meshes_.getMesh(meshNameEditing_);
        if (0 == submeshNameEditing_.length()) {
            if (0 == mesh.getNumSubmeshes()) {
                StatRenderer.setMiscStatMsg("pathEditor",
                        "ERROR: select or create submesh first");
            } else {
                submeshNameEditing_ = mesh.getFirstSubmeshName();
            }
        } else {
            submeshNameBefore = submeshNameEditing_;
            submeshNameEditing_ = mesh.getNextSubmeshName(submeshNameEditing_);
        }
        onEditingSubmeshChanged(submeshNameBefore);
    }

    private void selectPreviousSubmesh() {
        if (!canChangeEditingSubmesh()) {
            return;
        }
        PaintPathMesh mesh = meshes_.getMesh(meshNameEditing_);
        String submeshNameBefore = null;
        if (0 == submeshNameEditing_.length()) {
            if (0 == mesh.getNumSubmeshes()) {
                StatRenderer.setMiscStatMsg("pathEditor",
                        "ERROR: select or create submesh first");
            } else {
                submeshNameEditing_ = mesh.getLastSubmeshName();
            }
        } else {
            submeshNameBefore = submeshNameEditing_;
            submeshNameEditing_ = mesh
                    .getPreviousSubmeshName(submeshNameEditing_);
        }
        onEditingSubmeshChanged(submeshNameBefore);
    }

    private boolean canChangeEditingSubmesh() {
        if (0 == meshNameEditing_.length()) {
            StatRenderer.setMiscStatMsg("pathEditor",
                    "ERROR: select or create mesh first");
            return false;
        }
        return true;
    }

    private void onEditingSubmeshChanged(String submeshNameBefore) {
        PaintPathMesh mesh = meshes_.getMesh(meshNameEditing_);
        if (submeshNameBefore != null) {
            PaintPathSubmesh submeshBefore = mesh.getSubmesh(submeshNameBefore);
            if (submeshBefore.hidden_) {
                entityEditing_.getSubentity(submeshNameBefore).effectHidden_ = true;
            }
        }
        if (submeshNameEditing_.length() > 0) {
            PaintPathSubmesh submesh = mesh.getSubmesh(submeshNameEditing_);
            if (submesh.getNumElements() > 0) {
                elementIndexEditing_ = 0;
                moveCursorToElement();
            } else {
                elementIndexEditing_ = -1;
            }
            if (submesh.hidden_) {
                entityEditing_.getSubentity(submeshNameEditing_).effectHidden_ = false;
            }
        }
    }

    private void selectPreviousElement() {
        if (!canChangeEditingElement()) {
            return;
        }
        PaintPathMesh mesh = meshes_.getMesh(meshNameEditing_);
        PaintPathSubmesh submesh = mesh.getSubmesh(submeshNameEditing_);
        elementIndexEditing_--;
        if (elementIndexEditing_ < -1) {
            elementIndexEditing_ = submesh.getNumElements() - 1;
        }
        if (elementIndexEditing_ > -1) {
            moveCursorToElement();
        }
    }

    private void removeEditingElem() {
        PaintPathMesh mesh = meshes_.getMesh(meshNameEditing_);
        PaintPathSubmesh submesh = mesh.getSubmesh(submeshNameEditing_);
        int elementIndexToRemove = elementIndexEditing_;
        boolean success = submesh.removeElement(elementIndexToRemove);
        if (success) {
            StatRenderer.setMiscStatMsg("pathEditor",
                    "removed element " + elementIndexToRemove);
        } else {
            StatRenderer.setMiscStatMsg("pathEditor",
                    "Error remove element " + elementIndexToRemove);
        }
        if (elementIndexEditing_ >= submesh.getNumElements()) {
            elementIndexEditing_ = 0;
        }
    }

    private void removeEditingMesh() {
        if (meshes_.getNumMeshes() <= 1) {
            StatRenderer.setMiscStatMsg(
                    "pathEditor",
                    "ERROR: cannot remove mesh because only "
                            + meshes_.getNumMeshes() + " left");
            return;
        }
        String meshNameToRemove = meshNameEditing_;
        selectNextMesh();
        if (!meshes_.removeMesh(meshNameToRemove)) {
            StatRenderer.setMiscStatMsg("pathEditor", "ERROR in removing mesh "
                    + meshNameToRemove);
        }
    }

    private void removeEditingSubmesh() {
        PaintPathMesh mesh = meshes_.getMesh(meshNameEditing_);
        String submeshNameToRemove = submeshNameEditing_;
        selectNextSubmesh();
        if (mesh.removeSubmesh(submeshNameToRemove)) {
            entityEditing_.populateAllSubentities();
            StatRenderer.setMiscStatMsg("pathEditor", "removed submesh "
                    + submeshNameToRemove);
        } else {
            StatRenderer.setMiscStatMsg("pathEditor",
                    "ERROR in removing submesh " + submeshNameToRemove);
        }
    }

    private boolean canChangeEditingElement() {
        if (0 == meshNameEditing_.length()) {
            StatRenderer.setMiscStatMsg("pathEditor",
                    "ERROR: select or create mesh first");
            return false;
        }
        if (0 == submeshNameEditing_.length()) {
            StatRenderer.setMiscStatMsg("pathEditor",
                    "ERROR: select or create submesh first");
            return false;
        }
        return true;
    }

    private void selectNextElement() {
        if (!canChangeEditingElement()) {
            return;
        }
        PaintPathMesh mesh = meshes_.getMesh(meshNameEditing_);
        PaintPathSubmesh submesh = mesh.getSubmesh(submeshNameEditing_);
        elementIndexEditing_++;
        if (elementIndexEditing_ >= submesh.getNumElements()) {
            elementIndexEditing_ = -1;
        } else {
            moveCursorToElement();
        }
    }

    private boolean selectFirstAnimKey() {
        PaintPathMesh mesh = meshes_.getMesh(meshNameEditing_);
        if (submeshNameEditing_.length() == 0) {
            animKeyNameEditing_ = "";
        } else {
            PaintPathSubmesh submesh = mesh.getSubmesh(submeshNameEditing_);
            if (submesh.getNumAnimKeys() == 0) {
                animKeyNameEditing_ = "";
            } else {
                animKeyNameEditing_ = submesh.getFirstAnimKey();
            }
        }
        return onSubmeshAnimKeySet();
    }

    private boolean onSubmeshAnimKeySet() {
        if ((entityEditing_ == null) || (submeshNameEditing_.length() == 0)) {
            return false;
        } else {
            PaintPathSubentity subentity = entityEditing_
                    .getSubentity(submeshNameEditing_);
            if (subentity == null) {
                return false;
            }
            if (animKeyNameEditing_.length() == 0) {
                subentity.resetAnimTransform();
            } else {
                subentity.applyAnimKeyFromSubmesh(animKeyNameEditing_, animKeyPercent_, true);
            }
        }
        updateDialogScrollbarText();
        return true;
    }

    private void resetCurrentEntityAnim() {
        if ((entityEditing_ == null) || (submeshNameEditing_.length() == 0)) {
            // nop
        } else {
            entityEditing_.resetAllAnimTransform();
        }
        animTrackPlaying_ = false;
    }

    private void selectNextAnimKey() {
        PaintPathMesh mesh = meshes_.getMesh(meshNameEditing_);
        PaintPathSubmesh submesh = mesh.getSubmesh(submeshNameEditing_);
        if (submesh.getNumAnimKeys() == 0) {
            return;
        }
        animKeyNameEditing_ = submesh.getNextAnimKey(animKeyNameEditing_);
        onSubmeshAnimKeySet();
    }

    private void selectPreviousAnimKey() {
        PaintPathMesh mesh = meshes_.getMesh(meshNameEditing_);
        PaintPathSubmesh submesh = mesh.getSubmesh(submeshNameEditing_);
        if (submesh.getNumAnimKeys() == 0) {
            return;
        }
        animKeyNameEditing_ = submesh.getPreviousAnimKey(animKeyNameEditing_);
        onSubmeshAnimKeySet();
    }

    private void removeEditingAnimKey() {
        PaintPathMesh mesh = meshes_.getMesh(meshNameEditing_);
        PaintPathSubmesh submesh = mesh.getSubmesh(submeshNameEditing_);
        submesh.removeAnim(animKeyNameEditing_);
        StatRenderer.setMiscStatMsg("pathEditor", "removed anim, and now has " +
                submesh.getNumAnimKeys() + " anim keys");
        selectFirstAnimKey();
    }

    private void resetAnimTransform() {
        PaintPathMesh mesh = meshes_.getMesh(meshNameEditing_);
        PaintPathSubmesh submesh = mesh.getSubmesh(submeshNameEditing_);
        PaintPathSubmesh.AnimKey animTransform = submesh
                .getAnimKey(animKeyNameEditing_);
        if (TransformMode.TRANSLATION == transformMode_) {
            animTransform.resetTranslation();
            StatRenderer.setMiscStatMsg("pathEditor",
                    "TRANSLATION set to IDENTITY");
        } else if (TransformMode.ROTATION == transformMode_) {
            animTransform.resetRotation();
            StatRenderer.setMiscStatMsg("pathEditor",
                    "ROTATION set to IDENTITY");
        } else if (TransformMode.SCALE == transformMode_) {
            animTransform.resetScale();
            StatRenderer.setMiscStatMsg("pathEditor", "SCALE set to IDENTITY");
        } else if (TransformMode.__CURSOR_POS == transformMode_) {
            animTransform.reset();
            StatRenderer.setMiscStatMsg("pathEditor",
                    "transform set to IDENTITY");
        }
        submesh.replaceAnim(animKeyNameEditing_, animTransform);
        PaintPathSubentity subentity = entityEditing_
                .getSubentity(submeshNameEditing_);
        subentity.applyAnimKeyFromSubmesh(animKeyNameEditing_, animKeyPercent_, true);
    }

    private void onMeshAnimTrackSet() {
        if ((entityEditing_ == null) || (animTrackNameEditing_.length() == 0)) {
            // nop
        } else {
            entityEditing_.applyAnimTrackFromMesh(animTrackNameEditing_,
                    animTrackTimePos_, true);
        }

        updateDialogScrollbarText();
    }

    private void selectFirstAnimTrack() {
        animTrackNameEditing_ = "";
        PaintPathMesh mesh = meshes_.getMesh(meshNameEditing_);
        if (mesh.getNumAnimTracks() > 0) {
            animTrackNameEditing_ = mesh.getFirstAnimTrackName();
            animTrackTimePos_ = 0.f;
            animTrackPlaying_ = false;
        } else {
            StatRenderer.setMiscStatMsg("pathEditor", "mesh has no anim track");
        }
        onMeshAnimTrackSet();
    }

    private void selectNextAnimTrack() {
        PaintPathMesh mesh = meshes_.getMesh(meshNameEditing_);
        if (mesh.getNumAnimTracks() == 0) {
            return;
        }
        animTrackNameEditing_ = mesh
                .getNextAnimTrackName(animTrackNameEditing_);
        onMeshAnimTrackSet();
    }

    private void selectPreviousAnimTrack() {
        PaintPathMesh mesh = meshes_.getMesh(meshNameEditing_);
        if (mesh.getNumAnimTracks() == 0) {
            return;
        }
        animTrackNameEditing_ = mesh
                .getPrevAnimTrackName(animTrackNameEditing_);
        onMeshAnimTrackSet();
    }

    private void selectFirstEffect() {
        effectNameEditing_ = "";
        PaintPathMesh mesh = meshes_.getMesh(meshNameEditing_);
        if (mesh.getNumEffects() > 0) {
            effectNameEditing_ = mesh.getFirstEffectName();
            effectPercent_ = 0;
        } else {
            StatRenderer.setMiscStatMsg("pathEditor", "mesh has no effect");
        }
        onMeshEffectSet();
    }

    private void selectNextEffect() {
        PaintPathMesh mesh = meshes_.getMesh(meshNameEditing_);
        if (mesh.getNumEffects() == 0) {
            return;
        }
        effectNameEditing_ = mesh.getNextEffect(effectNameEditing_);
        onMeshEffectSet();
    }

    private void onMeshEffectSet() {
        if ((entityEditing_ == null) || (effectNameEditing_.length() == 0)) {
            // nop
        } else {
            entityEditing_.applyEffect(effectNameEditing_, effectPercent_);
        }
        updateDialogScrollbarText();
    }

    private void resetCurrentEffect() {
        if ((entityEditing_ == null) || (submeshNameEditing_.length() == 0)) {
            // nop
        } else {
            entityEditing_.resetAllEffect();
        }
    }

    @Override
    public void handleMessage(Message m) {
        String messageType = m.getData().getString("type");
        PaintPathEditorUtil.EventType eventType =
                PaintPathEditorUtil.EventType.valueOf(messageType);
        if (PaintPathEditorUtil.EventType.RENAME_MESH == eventType) {
        	PaintPathMesh mesh = meshes_.getMesh(meshNameEditing_); 
        	meshes_.removeMesh(meshNameEditing_);
        	Logger.logInfo("rename mesh from " + meshNameEditing_ + " to " + m.getData().getString("data"));
            meshNameEditing_ = m.getData().getString("data");
            meshes_.addMesh(meshNameEditing_, mesh);
            
            gameViewWidgetWrapper_.setGameStatusMsg("renamed to " + meshNameEditing_, 3);
        } else if (PaintPathEditorUtil.EventType.RENAME_SUBMESH == eventType) {
            submeshNameEditing_ = m.getData().getString("data");
        } else if (PaintPathEditorUtil.EventType.POPULATE_ALL_SUBENTITIES == eventType) {
            if (entityEditing_ != null) {
                entityEditing_.populateAllSubentities();
            }
        } else if (PaintPathEditorUtil.EventType.SET_EDIT_MODE == eventType) {
            String newMode = m.getData().getString("data");

            // reset
            if (meshElemEditMode_.toString().equals(
                    MeshElemEditMode.ANIM_KEY.toString()) &&
                    !newMode.toString().equals(MeshElemEditMode.ANIM_KEY.toString())) {
                resetCurrentEntityAnim();
                MainActivityBase.dialog_scrollbar_.setVisibility(View.INVISIBLE);
                animKeyNameEditing_ = "";
                updateDialogScrollbarText();
                transformMode_ = TransformMode.TRANSLATION;
            } else if (meshElemEditMode_.toString().equals(
                    MeshElemEditMode.ANIM_TRACK.toString()) &&
                    !newMode.toString().equals(MeshElemEditMode.ANIM_TRACK.toString())) {
                resetCurrentEntityAnim();
                MainActivityBase.dialog_scrollbar_.setVisibility(View.INVISIBLE);
                animTrackNameEditing_ = "";
                updateDialogScrollbarText();
                transformMode_ = TransformMode.TRANSLATION;
            } else if (meshElemEditMode_.toString().equals(
                    MeshElemEditMode.EFFECT.toString()) &&
                    !newMode.toString().equals(MeshElemEditMode.EFFECT.toString())) {
                resetCurrentEffect();
                MainActivityBase.dialog_scrollbar_.setVisibility(View.INVISIBLE);
                effectNameEditing_ = "";
            }

            // set new
            if (newMode.equals(MeshElemEditMode.ANIM_KEY.toString())) {
                MainActivityBase.dialog_scrollbar_.setVisibility(View.VISIBLE);
                animKeyPercent_ = MainActivityBase.dialog_scrollbar_SeekBar_MAX;
                MainActivityBase.dialog_scrollbar_SeekBar_
                        .setProgress(animKeyPercent_);
                if (submeshNameEditing_.length() == 0) {
                    StatRenderer.setMiscStatMsg("pathEditor", "ERROR: submesh not selected");
                } else {
                    boolean success = selectFirstAnimKey();
                    if (!success) {
                        StatRenderer.setMiscStatMsg(
                                        "pathEditor",
                                        "ERROR: probably editing entity does not have submesh");
                    } else {
                        meshElemEditMode_ = MeshElemEditMode.valueOf(newMode);
                    }
                }
            } else if (newMode.equals(MeshElemEditMode.ANIM_TRACK.toString())) {
                if (meshNameEditing_.length() == 0) {
                    StatRenderer.setMiscStatMsg("pathEditor", "ERROR: mesh not selected");
                } else {
                    meshElemEditMode_ = MeshElemEditMode.valueOf(newMode);
                    selectFirstAnimTrack();
                    MainActivityBase.dialog_scrollbar_.setVisibility(View.VISIBLE);
                    animTrackTimePos_ = 0;
                    MainActivityBase.dialog_scrollbar_SeekBar_.setProgress(0);
                }
            } else if (newMode.equals(MeshElemEditMode.EFFECT.toString())) {
                if (meshNameEditing_.length() == 0) {
                    StatRenderer.setMiscStatMsg("pathEditor", "ERROR: mesh not selected");
                } else {
                    meshElemEditMode_ = MeshElemEditMode.valueOf(newMode);
                    selectFirstEffect();
                    MainActivityBase.dialog_scrollbar_.setVisibility(View.VISIBLE);
                    effectPercent_ = 0;
                    MainActivityBase.dialog_scrollbar_SeekBar_.setProgress(0);
                }
            } else {
                meshElemEditMode_ = MeshElemEditMode.valueOf(m.getData().getString("data"));
            }
        } else if (PaintPathEditorUtil.EventType.UPDATE_ANIM_KEY_NAME == eventType) {
            animKeyNameEditing_ = m.getData().getString("data");
        } else if (PaintPathEditorUtil.EventType.CALL_updateDialogScrollbarText == eventType) {
            updateDialogScrollbarText();
        } else if (PaintPathEditorUtil.EventType.CLONE_MESH_RESULT == eventType) {
        	gameViewWidgetWrapper_.setGameStatusMsg(m.getData().getString("data"), 3);
        }
    }

    private void drawEntity(Canvas canvas, Paint paint, Camera camera) {
        if (meshNameEditing_.length() == 0) {
            if (entityEditing_ != null) {
                entityEditing_ = null;
            }
        } else {
            if (null == entityEditing_) {
                PaintPathMesh mesh = meshes_.getMesh(meshNameEditing_);
                entityEditing_ = new PaintPathEntity(mesh);
                entityEditing_.setPosition(0, 0);
            }
            entityEditing_.draw(canvas, paint, camera);
        }
    }

    private void moveCursorToElement() {
        PaintPathMesh mesh = meshes_.getMesh(meshNameEditing_);
        PaintPathSubmesh submesh = mesh.getSubmesh(submeshNameEditing_);
        PaintPathElement elementEditing = submesh
                .getElement(elementIndexEditing_);
        cursorPosDeciX_ = elementEditing.posDeciX_;
        cursorPosDeciY_ = elementEditing.posDeciY_;
    }

    private void drawCursor(Canvas canvas, Paint paint, Camera camera) {
        float rotationDeg = 0;
        float scale = 1.f;
        float strokeWidth = 2.f;
        if (0 == meshNameEditing_.length()) {
            rotationDeg = 45.f;
            cursorPosDeciX_ = (int) (camera.cameraState_.posX_ * 10.f);
            cursorPosDeciY_ = 200;
            scale = 3.f;
            strokeWidth = 5.f;
            cursorColorIndex_ = 3;
        } else if (0 == submeshNameEditing_.length()) {
            rotationDeg = 45.f;
            cursorPosDeciX_ = (int) (camera.cameraState_.posX_ * 10.f);
            cursorPosDeciY_ = 20;
            strokeWidth = 4.f;
            cursorColorIndex_ = 2;
        } else {
            String cursorMsg = "at " + (cursorPosDeciX_ < 0 ? "-" : "") +
                    Math.abs(cursorPosDeciX_) / 10 + "." +
                    Math.abs(cursorPosDeciX_ % 10) +
                    ", " + (cursorPosDeciY_ < 0 ? "-" : "") +
                    Math.abs(cursorPosDeciY_) / 10 + "."
                    + Math.abs(cursorPosDeciY_ % 10);
            if (MeshElemEditMode.ANIM_KEY == meshElemEditMode_) {
                cursorMsg += " / anim key: " + animKeyNameEditing_;
                if (animKeyNameEditing_.length() > 0) {
                    cursorMsg += " / " + transformMode_.toString();
                }
            } else if (MeshElemEditMode.ANIM_TRACK == meshElemEditMode_) {
                cursorMsg += " / anim track: " + animTrackNameEditing_;
            } else if (MeshElemEditMode.EFFECT == meshElemEditMode_) {
                cursorMsg += " / anim effect: " + effectNameEditing_;
            } else {
                cursorMsg += " / edit mode: " + meshElemEditMode_.toString();
            }
            StatRenderer.setMiscStatMsg("cursor", cursorMsg);

            cursorColorIndex_ = 0;
            if (-1 == elementIndexEditing_) {
                scale = 2.f;
                strokeWidth = 1.f;
                cursorColorIndex_ = 1;
            } else {
                if (meshElemEditMode_.toString().startsWith("DUP")) {
                    strokeWidth = 6.f;
                    cursorColorIndex_ = 5;
                } else if (meshElemEditMode_.toString().startsWith("SCALE")) {
                    strokeWidth = 6.f;
                    cursorColorIndex_ = 4;
                } else if (MeshElemEditMode.ANIM_KEY == meshElemEditMode_) {
                    strokeWidth = 1.f;
                    cursorColorIndex_ = 6;
                } else if (MeshElemEditMode.ANIM_TRACK == meshElemEditMode_) {
                    strokeWidth = 1.f;
                    cursorColorIndex_ = 7;
                } else if (MeshElemEditMode.EFFECT == meshElemEditMode_) {
                    strokeWidth = 1.f;
                    cursorColorIndex_ = 7;
                }
            }
        }

        Matrix cursorTransform = camera.getViewportTransform(rotationDeg,
                (float) cursorPosDeciX_ / 10.f, (float) cursorPosDeciY_ / 10.f, scale, scale);
        transformedCursorPath_.rewind();
        originalCursorPath_.transform(cursorTransform, transformedCursorPath_);

        long cursorBlinkTime = lastCursorBlinkTime_ + 700;
        if (SystemClock.elapsedRealtime() > cursorBlinkTime) {
            cursorColorBlinkIndex_ += 1;
            if (cursorColorBlinkIndex_ > 1) {
                cursorColorBlinkIndex_ = 0;
            }
            lastCursorBlinkTime_ = SystemClock.elapsedRealtime();
        }
        if (cursorColorBlinkIndex_ == 0) {
            paint.setColor(cursorColorArray_[cursorColorIndex_]);
        } else {
            int blinkColor = Color.argb(
                    Color.alpha(cursorColorArray_[cursorColorIndex_]) / 3,
                    Color.red(cursorColorArray_[cursorColorIndex_]) / 3,
                    Color.green(cursorColorArray_[cursorColorIndex_] / 3),
                    Color.blue(cursorColorArray_[cursorColorIndex_] / 3)
                    );
            paint.setColor(blinkColor);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        canvas.drawPath(transformedCursorPath_, paint);
    }

    private void setMenuMsg() {
        if (0 == meshNameEditing_.length()) {
            StatRenderer.setMiscStatMsg("editing now", "???");
            String menuStr = new String("N)ew mesh");
            if (meshes_.getNumMeshes() > 0) {
                menuStr += ", } next mesh, C)lone mesh";
            }
            StatRenderer.setMiscStatMsg("menu", menuStr);
        } else if (0 == submeshNameEditing_.length()) {
            PaintPathMesh mesh = meshes_.getMesh(meshNameEditing_);
            StatRenderer.setMiscStatMsg("editing now", meshNameEditing_ + " / ???");
            String menuStr = new String(
                    "N)ew submesh, R)emove mesh, n)ame mesh, } next mesh");
            if (mesh.getNumSubmeshes() > 0) {
                menuStr += ", ] submesh";
            }
            StatRenderer.setMiscStatMsg("menu", menuStr);
        } else {
            PaintPathMesh mesh = meshes_.getMesh(meshNameEditing_);
            PaintPathSubmesh submesh = mesh.getSubmesh(submeshNameEditing_);
            if (MeshElemEditMode.ANIM_KEY == meshElemEditMode_) {
                StatRenderer.setMiscStatMsg("editing now", meshNameEditing_ +
                        " / " + submeshNameEditing_);
                String menuStr = new String("arrows, mod)e");
                if (animKeyNameEditing_.length() > 0) {
                    menuStr += ", R)emove, n)ame";
                }
                menuStr += ", = nextAnimKey, a)dd anim, t/r/s/c/i";
                StatRenderer.setMiscStatMsg("menu", menuStr);
            } else if (MeshElemEditMode.ANIM_TRACK == meshElemEditMode_) {
                StatRenderer.setMiscStatMsg("editing now", meshNameEditing_ +
                        " / " + submeshNameEditing_);
                String menuStr = new String("p)lay or stop, =)next, mod)e");
                StatRenderer.setMiscStatMsg("menu", menuStr);
            } else if (elementIndexEditing_ == -1) {
                StatRenderer.setMiscStatMsg("editing now", meshNameEditing_ +
                        " / " + submeshNameEditing_);
                String menuStr = new String("arrows, mod)e");
                if (!mesh.hasAnyElementAtPos(cursorPosDeciX_, cursorPosDeciY_, 3)) {
                    if ((submesh != null) && (submesh.getNumElements() == 0)) {
                        menuStr += ", append m)oveTo";
                    } else {
                        menuStr += ", append l)ineTo";
                    }
                }
                menuStr += ", R)emove, n)ame, = el, ] sm";
                StatRenderer.setMiscStatMsg("menu", menuStr);
            } else {
                StatRenderer.setMiscStatMsg("editing now", meshNameEditing_ +
                        " / " + submeshNameEditing_ + " / elem_"
                        + elementIndexEditing_);
                String menuStr = new String("arrows, mod)e");
                if (elementIndexEditing_ > 0) {
                    menuStr += ", R)emove, i)nsert, c)olor";
                }
                menuStr += ", n)ame, = next el, ] next sm";
                StatRenderer.setMiscStatMsg("menu", menuStr);
            }
        }
    }

    private class OnDialogScrollbarSeekBarChangedHandler implements
            OnSeekBarChangeListener {
        public void onProgressChanged(SeekBar seekBar, int progress,
                boolean fromUser) {
            if (!fromUser) {
                return;
            }

            if (MeshElemEditMode.ANIM_KEY == meshElemEditMode_) {
                animKeyPercent_ = MainActivityBase.dialog_scrollbar_SeekBar_
                        .getProgress() * 100
                        / MainActivityBase.dialog_scrollbar_SeekBar_MAX;
                updateDialogScrollbarText();
                if ((entityEditing_ != null)
                        && (animKeyNameEditing_.length() != 0)) {
                    PaintPathSubentity subentity = entityEditing_.getSubentity(submeshNameEditing_);
                    subentity.applyAnimKeyFromSubmesh(animKeyNameEditing_, animKeyPercent_, true);
                }
            } else if (MeshElemEditMode.ANIM_TRACK == meshElemEditMode_) {
                if ((entityEditing_ != null)
                        && (animTrackNameEditing_.length() > 0)) {
                    PaintPathMesh mesh = meshes_.getMesh(meshNameEditing_);
                    PaintPathMesh.AnimTrack animTrack = mesh
                            .getAnimTrack(animTrackNameEditing_);
                    animTrackTimePos_ = (float) MainActivityBase.dialog_scrollbar_SeekBar_
                            .getProgress()
                            / (float) MainActivityBase.dialog_scrollbar_SeekBar_MAX
                            * animTrack.getTimeLengh();
                    frameRenderLock_ = true;
                    entityEditing_.applyAnimTrackFromMesh(
                            animTrackNameEditing_, animTrackTimePos_, true);
                    frameRenderLock_ = false;
                } else {
                    animTrackTimePos_ = 0.f;
                }
                updateDialogScrollbarText();
            } else if (MeshElemEditMode.EFFECT == meshElemEditMode_) {
                if ((entityEditing_ != null)
                        && (effectNameEditing_.length() > 0)) {
                    effectPercent_ = MainActivityBase.dialog_scrollbar_SeekBar_
                            .getProgress() * 100 /
                            MainActivityBase.dialog_scrollbar_SeekBar_MAX;
                    frameRenderLock_ = true;
                    entityEditing_.applyEffect(effectNameEditing_, effectPercent_);
                    frameRenderLock_ = false;
                } else {
                    effectPercent_ = 0;
                }
                updateDialogScrollbarText();
            }
        }

        public void onStartTrackingTouch(SeekBar arg0) {
        }

        public void onStopTrackingTouch(SeekBar arg0) {
        }
    }

    private OnDialogScrollbarSeekBarChangedHandler onDialogScrollbarSeekBarChangedHandler_;

    private void updateDialogScrollbarText() {
        if (entityEditing_ == null) {
            MainActivityBase.dialog_scrollbar_textView_.setText("No entity");
        } else if (MeshElemEditMode.ANIM_KEY == meshElemEditMode_) {
            if (animKeyNameEditing_.length() == 0) {
                MainActivityBase.dialog_scrollbar_textView_.setText("No anim key");
            } else {
                MainActivityBase.dialog_scrollbar_textView_.setText("anim "
                        + animKeyPercent_ + "%");
            }
        } else if (MeshElemEditMode.ANIM_TRACK == meshElemEditMode_) {
            if (animTrackNameEditing_.length() == 0) {
                MainActivityBase.dialog_scrollbar_textView_.setText("No anim track");
            } else {
                MainActivityBase.dialog_scrollbar_textView_.setText("anim "
                        + (int) animTrackTimePos_ + "." +
                        (int) (animTrackTimePos_ * 100.f) % 100 + "sec");
            }
        } else if (MeshElemEditMode.EFFECT == meshElemEditMode_) {
            if (effectNameEditing_.length() == 0) {
                MainActivityBase.dialog_scrollbar_textView_.setText("No effect");
            } else {
                MainActivityBase.dialog_scrollbar_textView_.setText("effect "
                        + (int) effectPercent_ + "%");
            }
        }
    }
}
