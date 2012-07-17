package hyperden.mesh_entity_paint;

import hyperden.heli_one.app.air_combat.R;
import hyperden.heli_one.renderer.StatRenderer;
import hyperden.mesh_entity_paint.PaintPathEditor.MeshElemEditMode;
import hyperden.util.Logger;

import java.util.Iterator;
import java.util.Set;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

public class PaintPathEditorUtil {

    public enum EventType {
        RENAME_MESH,
        RENAME_SUBMESH,
        POPULATE_ALL_SUBENTITIES,
        SET_EDIT_MODE,
        UPDATE_ANIM_KEY_NAME,
        CALL_updateDialogScrollbarText,
        CLONE_MESH_RESULT,
    }

    private PaintPathMeshes meshes_;

    public PaintPathEditorUtil(PaintPathMeshes meshes) {
        meshes_ = meshes;
    }

    public void setColorEditingSubmesh(Context context,
            final String meshNameEditing,
            final String submeshNameEditing) {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(context);
        PaintPathMesh mesh = meshes_.getMesh(meshNameEditing);
        PaintPathSubmesh submesh = mesh.getSubmesh(submeshNameEditing);
        dialogBuilder.setTitle("Input color");
        LayoutInflater factory = LayoutInflater.from(context);
        final View colorDialogView = factory.inflate(
                R.layout.alert_dialog_color_entry, null);
        EditText editRed = (EditText) colorDialogView.findViewById(
                R.id.alert_dialog_color_edit_red);
        if (editRed == null) {
        	return;
        }
        if (submesh == null) {
        	return;
        }
        editRed.setText(Integer.toString(Color.red(submesh.color_)));
        EditText editGreen = (EditText) colorDialogView.findViewById(
                R.id.alert_dialog_color_edit_green);
        editGreen.setText(Integer.toString(Color.green(submesh.color_)));
        EditText editBlue = (EditText) colorDialogView.findViewById(
                R.id.alert_dialog_color_edit_blue);
        editBlue.setText(Integer.toString(Color.blue(submesh.color_)));
        EditText editAlpha = (EditText) colorDialogView.findViewById(
                R.id.alert_dialog_color_edit_alpha);
        editAlpha.setText(Integer.toString(Color.alpha(submesh.color_)));
        EditText editThickness = (EditText) colorDialogView.findViewById(
                R.id.alert_dialog_color_edit_thickness);
        editThickness.setText(Integer.toString(submesh.strokeWidth_));
        dialogBuilder.setPositiveButton("OK", new OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                EditText editRed = (EditText) colorDialogView.findViewById(
                        R.id.alert_dialog_color_edit_red);
                SpannableStringBuilder spRed = (SpannableStringBuilder) editRed
                        .getText();
                if (spRed.toString().length() == 0) {
                    return;
                }
                int valRed = Integer.parseInt(spRed.toString());

                EditText editGreen = (EditText) colorDialogView.findViewById(
                        R.id.alert_dialog_color_edit_green);
                SpannableStringBuilder spGreen = (SpannableStringBuilder) editGreen
                        .getText();
                if (spGreen.toString().length() == 0) {
                    return;
                }
                int valGreen = Integer.parseInt(spGreen.toString());

                EditText editBlue = (EditText) colorDialogView.findViewById(
                        R.id.alert_dialog_color_edit_blue);
                SpannableStringBuilder spBlue = (SpannableStringBuilder) editBlue
                        .getText();
                if (spBlue.toString().length() == 0) {
                    return;
                }
                int valBlue = Integer.parseInt(spBlue.toString());

                EditText editAlpha = (EditText) colorDialogView.findViewById(
                        R.id.alert_dialog_color_edit_alpha);
                SpannableStringBuilder spAlpha = (SpannableStringBuilder) editAlpha
                        .getText();
                if (spAlpha.toString().length() == 0) {
                    return;
                }
                int valAlpha = Integer.parseInt(spAlpha.toString());
                int valARGB = Color.argb(valAlpha, valRed, valGreen, valBlue);

                EditText editThickness = (EditText) colorDialogView
                        .findViewById(
                        R.id.alert_dialog_color_edit_thickness);
                SpannableStringBuilder spThickness = (SpannableStringBuilder) editThickness
                        .getText();
                if (spThickness.toString().length() == 0) {
                    return;
                }
                int valThickness = Integer.parseInt(spThickness.toString());

                PaintPathMesh mesh = meshes_.getMesh(meshNameEditing);
                PaintPathSubmesh submesh = mesh.getSubmesh(submeshNameEditing);
                submesh.setColor(valARGB);
                if (valThickness == 0) {
                    submesh.setPaintStyle(Paint.Style.FILL);
                    StatRenderer.setMiscStatMsg("pathEditor", "set color to "
                            + valARGB
                            + " and paint style FILL");
                } else {
                    submesh.setPaintStyle(Paint.Style.STROKE);
                    submesh.setStrokeWidth(valThickness);
                    StatRenderer.setMiscStatMsg("pathEditor", "set color to "
                            + valARGB
                            + " and paint style STROKE with thickness "
                            + valThickness);
                }
            }
        });
        dialogBuilder.setCancelable(true);
        dialogBuilder.setView(colorDialogView);
        dialogBuilder.show();
    }

    public void renameEditingSubmesh(Context context,
            final String meshNameEditing,
            final String submeshNameEditing, final Handler dialogHandler) {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(context);
        dialogBuilder.setTitle("Input submesh name");
        dialogBuilder.setMessage("current name: " + submeshNameEditing);
        LayoutInflater factory = LayoutInflater.from(context);
        final View textDialogView = factory.inflate(
                R.layout.alert_dialog_text_entry, null);
        EditText editEntry = (EditText) textDialogView.findViewById(
                R.id.alert_dialog_text_edit);
        editEntry.setText(submeshNameEditing);
        dialogBuilder.setPositiveButton("OK", new OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                EditText editText = (EditText) textDialogView.findViewById(
                        R.id.alert_dialog_text_edit);
                Editable editable = editText.getText();
                SpannableStringBuilder spannable = (SpannableStringBuilder) editable;
                PaintPathMesh mesh = meshes_.getMesh(meshNameEditing);
                PaintPathSubmesh submesh = mesh.getSubmesh(submeshNameEditing);
                if (spannable.toString().length() > 0) {
                    mesh.removeSubmesh(submeshNameEditing);
                    mesh.addSubmesh(spannable.toString(), submesh);

                    Message handlerMsg = dialogHandler.obtainMessage();
                    Bundle b = new Bundle();
                    b.putString("type", EventType.RENAME_SUBMESH.toString());
                    b.putString("data", spannable.toString());
                    handlerMsg.setData(b);
                    dialogHandler.sendMessage(handlerMsg);
                }
            }
        });
        dialogBuilder.setCancelable(true);
        dialogBuilder.setView(textDialogView);
        dialogBuilder.show();
    }

    public void renameEditingMesh(Context context,
            final String meshNameEditing,
            final String submeshNameEditing, final Handler dialogHandler) {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(context);
        dialogBuilder.setTitle("Input mesh name");
        dialogBuilder.setMessage("current name: " + meshNameEditing);
        LayoutInflater factory = LayoutInflater.from(context);
        final View textDialogView = factory.inflate(
                R.layout.alert_dialog_text_entry, null);
        dialogBuilder.setPositiveButton("OK", new OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                EditText editText = (EditText) textDialogView.findViewById(
                        R.id.alert_dialog_text_edit);
                Editable editable = editText.getText();
                SpannableStringBuilder spannable = (SpannableStringBuilder) editable;
                if (spannable.toString().length() > 0) {
                	// do not rename here - may crash because rendered in different thread
                    Message handlerMsg = dialogHandler.obtainMessage();
                    Bundle b = new Bundle();
                    b.putString("type", EventType.RENAME_MESH.toString());
                    b.putString("data", spannable.toString());
                    handlerMsg.setData(b);
                    dialogHandler.sendMessage(handlerMsg);
                }
            }
        });
        dialogBuilder.setCancelable(true);
        dialogBuilder.setView(textDialogView);
        dialogBuilder.show();
    }
    
    public void cloneMesh(Context context, final Handler dialogHandler) {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(context);
        dialogBuilder.setTitle("Input mesh name");
        dialogBuilder.setMessage("to clone from...");
        LayoutInflater factory = LayoutInflater.from(context);
        final View textDialogView = factory.inflate(R.layout.alert_dialog_text_entry, null);
        dialogBuilder.setPositiveButton("OK", new OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                EditText editText = (EditText) textDialogView.findViewById(
                        R.id.alert_dialog_text_edit);
                Editable editable = editText.getText();
                SpannableStringBuilder spannable = (SpannableStringBuilder) editable;
                String resultStr = "?";
                if ((spannable.toString().length() > 0) && (meshes_.hasMesh(spannable.toString()))) {
                	Logger.logInfo("start cloning mesh " + spannable.toString());
                	PaintPathMesh mesh = meshes_.getMesh(spannable.toString());
                	String clonedMeshName = spannable.toString() + "_cloned";
                	PaintPathMesh clonedMesh = mesh.clone();
                	meshes_.addMesh(clonedMeshName, clonedMesh);
                	resultStr = "cloned to " + clonedMeshName;
                } else {
                	resultStr = "error";
                }
                
                Message handlerMsg = dialogHandler.obtainMessage();
                Bundle b = new Bundle();
                b.putString("type", EventType.CLONE_MESH_RESULT.toString());
                b.putString("data", resultStr);
                handlerMsg.setData(b);
                dialogHandler.sendMessage(handlerMsg);
            }
        });
        dialogBuilder.setCancelable(true);
        dialogBuilder.setView(textDialogView);
        dialogBuilder.show();
    }

    public boolean moveMeshElem(
            PaintPathEditor.MeshElemEditMode meshElemMoveMode,
            final int elementIndexEditing,
            final String meshNameEditing, final String submeshNameEditing,
            final String animKeyNameEditing, PaintPathEntity entityEditing,
            int diffDeciX, int diffDeciY,
            PaintPathEditor.TransformMode transformMode,
            float cursorPosX, float cursorPosY, int animKeyPercent,
            final Handler dialogHandler) {
        if ((null == meshNameEditing) || (null == submeshNameEditing)) {
            return false;
        }
        if ((meshNameEditing.length() == 0)
                || (submeshNameEditing.length() == 0)) {
            return false;
        }
        PaintPathMesh mesh = meshes_.getMesh(meshNameEditing);
        PaintPathSubmesh submesh = mesh.getSubmesh(submeshNameEditing);
        if ((PaintPathEditor.MeshElemEditMode.MOVE_ELEM == meshElemMoveMode) &&
                (elementIndexEditing >= 0) && (submesh.getNumElements() > 0)) {
            PaintPathElement elem = submesh.getElement(elementIndexEditing);
            elem.posDeciX_ += diffDeciX;
            elem.posDeciY_ += diffDeciY;
            submesh.computeOriginalPath();
            return true;
        } else if ((PaintPathEditor.MeshElemEditMode.MOVE_SUBMESH == meshElemMoveMode)
                &&
                (submesh.getNumElements() > 0)) {
            submesh.moveAllElements(diffDeciX, diffDeciY);
            return true;
        } else if ((PaintPathEditor.MeshElemEditMode.SCALE_SUBMESH == meshElemMoveMode)
                &&
                (submesh.getNumElements() > 0)) {
            submesh.scaleAllElements(diffDeciX, diffDeciY);
            StatRenderer.setMiscStatMsg("scale",
                    "submesh bbox: " + submesh.aabb_.toString()
                            + ", w" + submesh.aabb_.width() + " h"
                            + submesh.aabb_.height()
                            + " c" + submesh.aabb_.centerX() + ", "
                            + submesh.aabb_.centerY());
            return true;
        } else if ((PaintPathEditor.MeshElemEditMode.MOVE_MESH == meshElemMoveMode)
                &&
                (mesh.getNumSubmeshes() > 0)) {
            Set<String> submeshNames = mesh.getAllSubmeshNames();
            Iterator<String> submeshNameIter = submeshNames.iterator();
            while (submeshNameIter.hasNext()) {
                String submeshName = submeshNameIter.next();
                PaintPathSubmesh submeshIter = mesh.getSubmesh(submeshName);
                submeshIter.moveAllElements(diffDeciX, diffDeciY);
            }
            mesh.computeAabb();
            StatRenderer
                    .setMiscStatMsg("move",
                            "mesh bbox: " + mesh.aabb_
                                    + ", w" + mesh.aabb_.width() + " h"
                                    + mesh.aabb_.height()
                                    + " c" + mesh.aabb_.centerX() + ", "
                                    + mesh.aabb_.centerY());
            return true;
        } else if ((PaintPathEditor.MeshElemEditMode.SCALE_MESH == meshElemMoveMode)
                &&
                (mesh.getNumSubmeshes() > 0)) {
            Set<String> submeshNames = mesh.getAllSubmeshNames();
            Iterator<String> submeshNameIter = submeshNames.iterator();
            while (submeshNameIter.hasNext()) {
                String submeshName = submeshNameIter.next();
                PaintPathSubmesh submeshIter = mesh.getSubmesh(submeshName);
                submeshIter.scaleAllElements(diffDeciX, diffDeciY);
            }
            mesh.computeAabb();
            StatRenderer.setMiscStatMsg("scale",
                            "mesh bbox: " + mesh.aabb_
                                    + ", w" + mesh.aabb_.width() + " h"
                                    + mesh.aabb_.height()
                                    + " c" + mesh.aabb_.centerX() + ", "
                                    + mesh.aabb_.centerY());
            return true;
        } else if ((PaintPathEditor.MeshElemEditMode.DUP_AS_IS == meshElemMoveMode)
                &&
                (mesh.getNumSubmeshes() > 0)) {
            PaintPathSubmesh dupSubmesh = new PaintPathSubmesh();
            dupSubmesh.color_ = submesh.color_;
            dupSubmesh.paintStyle_ = submesh.paintStyle_;
            dupSubmesh.strokeWidth_ = submesh.strokeWidth_;
            for (int i = 0; i < submesh.getNumElements(); i++) {
                PaintPathElement dupElem = submesh.getElement(i).clone();
                dupElem.posDeciX_ += diffDeciX * 10;
                dupElem.posDeciY_ += diffDeciY * 10;
                dupSubmesh.appendElement(dupElem);
            }
            mesh.addSubmesh(submeshNameEditing + "_dup", dupSubmesh);
            mesh.computeAabb();

            Message handlerMsg = dialogHandler.obtainMessage();
            Bundle b = new Bundle();
            b.putString("type", EventType.POPULATE_ALL_SUBENTITIES.toString());
            handlerMsg.setData(b);
            dialogHandler.sendMessage(handlerMsg);

            return true;
        } else if ((PaintPathEditor.MeshElemEditMode.DUP_HORZ_MIRROR == meshElemMoveMode) &&
                (mesh.getNumSubmeshes() > 0)) {
            PaintPathSubmesh dupSubmesh = new PaintPathSubmesh();
            dupSubmesh.color_ = submesh.color_;
            dupSubmesh.paintStyle_ = submesh.paintStyle_;
            dupSubmesh.strokeWidth_ = submesh.strokeWidth_;
            for (int i = 0; i < submesh.getNumElements(); i++) {
                PaintPathElement dupElem = submesh.getElement(i).clone();
                dupElem.posDeciX_ += (submesh.aabb_.centerX() - dupElem.posDeciX_) * 2.f;
                dupElem.posDeciX_ += diffDeciX;
                dupElem.posDeciY_ += diffDeciY;
                dupSubmesh.appendElement(dupElem);
            }
            mesh.addSubmesh(submeshNameEditing + "_dup", dupSubmesh);
            mesh.computeAabb();

            Message handlerMsg = dialogHandler.obtainMessage();
            Bundle b = new Bundle();
            b.putString("type", EventType.POPULATE_ALL_SUBENTITIES.toString());
            handlerMsg.setData(b);
            dialogHandler.sendMessage(handlerMsg);

            return true;
        } else if ((PaintPathEditor.MeshElemEditMode.DUP_VERT_MIRROR == meshElemMoveMode) &&
                (mesh.getNumSubmeshes() > 0)) {
            PaintPathSubmesh dupSubmesh = new PaintPathSubmesh();
            dupSubmesh.color_ = submesh.color_;
            dupSubmesh.paintStyle_ = submesh.paintStyle_;
            dupSubmesh.strokeWidth_ = submesh.strokeWidth_;
            for (int i = 0; i < submesh.getNumElements(); i++) {
                PaintPathElement dupElem = submesh.getElement(i).clone();
                dupElem.posDeciX_ += diffDeciX;
                dupElem.posDeciY_ += (submesh.aabb_.centerY() - dupElem.posDeciY_) * 2.f;
                dupElem.posDeciY_ += diffDeciY;
                dupSubmesh.appendElement(dupElem);
            }
            mesh.addSubmesh(submeshNameEditing + "_dup", dupSubmesh);
            mesh.computeAabb();

            Message handlerMsg = dialogHandler.obtainMessage();
            Bundle b = new Bundle();
            b.putString("type", EventType.POPULATE_ALL_SUBENTITIES.toString());
            handlerMsg.setData(b);
            dialogHandler.sendMessage(handlerMsg);

            return true;
        } else if ((PaintPathEditor.MeshElemEditMode.ANIM_KEY == meshElemMoveMode) &&
                (submeshNameEditing.length() > 0)
                && (animKeyNameEditing.length() > 0)) {
            PaintPathSubmesh.AnimKey animTransform =
                    submesh.getAnimKey(animKeyNameEditing);
            if (transformMode == PaintPathEditor.TransformMode.TRANSLATION) {
                animTransform.translationX_ += (float)diffDeciX / 10.f;
                animTransform.translationY_ += (float)diffDeciY / 10.f;
            } else if (transformMode == PaintPathEditor.TransformMode.ROTATION) {
                animTransform.rotationDeg_ += diffDeciX;
                animTransform.rotationPivotX_ = cursorPosX;
                animTransform.rotationPivotY_ = cursorPosY;
            } else if (transformMode == PaintPathEditor.TransformMode.SCALE) {
                animTransform.scaleX_ += diffDeciX / 300.f;
                animTransform.scaleY_ += diffDeciY / 300.f;
                animTransform.scalePivotX_ = cursorPosX;
                animTransform.scalePivotY_ = cursorPosY;
            } else if (transformMode == PaintPathEditor.TransformMode.__CURSOR_POS) {
                return false;
            }
            submesh.replaceAnim(animKeyNameEditing, animTransform);
            PaintPathSubentity subentity = entityEditing
                    .getSubentity(submeshNameEditing);
            subentity.applyAnimKeyFromSubmesh(animKeyNameEditing, animKeyPercent, true);

            return true;
        }
        return false;
    }

    public void selectNextOrPrevMode(Context context, boolean backward,
            final Handler dialogHandler) {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(context);
        dialogBuilder.setTitle("PaintPathMesh Editor");
        dialogBuilder.setSingleChoiceItems(R.array.PPE_edit_modes, 0,
            new OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    PaintPathEditor.MeshElemEditMode newMode = MeshElemEditMode
                            .values()[which];
                    Message handlerMsg = dialogHandler.obtainMessage();
                    Bundle b = new Bundle();
                    b.putString("type", EventType.SET_EDIT_MODE.toString());
                    b.putString("data", newMode.toString());
                    handlerMsg.setData(b);
                    dialogHandler.sendMessage(handlerMsg);

                    dialog.cancel();
                }
            });
        dialogBuilder.setCancelable(true);
        dialogBuilder.show();
    }

    public void addNewAnimKey(final String meshNameEditing,
            final String submeshNameEditing,
            Context context, final Handler dialogHandler) {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(context);
        dialogBuilder.setTitle("Input new anim key name");
        LayoutInflater factory = LayoutInflater.from(context);
        final View textDialogView = factory.inflate(
                R.layout.alert_dialog_text_entry, null);
        dialogBuilder.setPositiveButton("OK", new OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                EditText editText = (EditText) textDialogView.findViewById(R.id.alert_dialog_text_edit);
                Editable editable = editText.getText();
                SpannableStringBuilder spannable = (SpannableStringBuilder) editable;
                PaintPathMesh mesh = meshes_.getMesh(meshNameEditing);
                PaintPathSubmesh submesh = mesh.getSubmesh(submeshNameEditing);
                if (spannable.toString().length() > 0) {
                    if (!submesh.hasAnimTransform(spannable.toString())) {
                        submesh.addNewAnimKey(spannable.toString());
                        StatRenderer.setMiscStatMsg("pathEditor",
                                "added anim, and now has " +
                                        submesh.getNumAnimKeys()
                                        + " anim key(s)");

                        Message handlerMsg = dialogHandler.obtainMessage();
                        Bundle b = new Bundle();
                        b.putString("type",
                                EventType.UPDATE_ANIM_KEY_NAME.toString());
                        b.putString("data", spannable.toString());
                        handlerMsg.setData(b);
                        dialogHandler.sendMessage(handlerMsg);
                    } else {
                        StatRenderer.setMiscStatMsg("pathEditor",
                                "did not added anim (key already exists");
                    }
                }
            }
        });
        dialogBuilder.setCancelable(true);
        dialogBuilder.setView(textDialogView);
        dialogBuilder.show();
    }

    public void renameEditingAnimKey(final String meshNameEditing,
            final String submeshNameEditing,
            Context context, final String animKeyNameEditing,
            final Handler dialogHandler) {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(context);
        dialogBuilder.setTitle("Input anim key name");
        dialogBuilder.setMessage("current name: " + animKeyNameEditing);
        LayoutInflater factory = LayoutInflater.from(context);
        final View textDialogView = factory.inflate(
                R.layout.alert_dialog_text_entry, null);
        dialogBuilder.setPositiveButton("OK", new OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                EditText editText = (EditText) textDialogView.findViewById(
                        R.id.alert_dialog_text_edit);
                Editable editable = editText.getText();
                SpannableStringBuilder spannable = (SpannableStringBuilder) editable;
                PaintPathMesh mesh = meshes_.getMesh(meshNameEditing);
                PaintPathSubmesh submesh = mesh.getSubmesh(submeshNameEditing);
                if (spannable.toString().length() > 0) {
                    if (!submesh.hasAnimTransform(spannable.toString())) {
                        PaintPathSubmesh.AnimKey animTransform =
                                submesh.getAnimKey(animKeyNameEditing);
                        submesh.removeAnim(animKeyNameEditing);
                        submesh.addAnimKey(spannable.toString(), animTransform);

                        Message handlerMsg = dialogHandler.obtainMessage();
                        Bundle b = new Bundle();
                        b.putString("type",
                                EventType.UPDATE_ANIM_KEY_NAME.toString());
                        b.putString("data", spannable.toString());
                        handlerMsg.setData(b);
                        dialogHandler.sendMessage(handlerMsg);
                    } else {
                        StatRenderer.setMiscStatMsg("pathEditor",
                                "did not change anim (key does not exist");
                    }
                }
            }
        });
        dialogBuilder.setCancelable(true);
        dialogBuilder.setView(textDialogView);
        dialogBuilder.show();
    }
}
