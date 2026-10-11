import { View } from '@nativescript/core';

/** Android native canvas owns its bitmap and worker lifetime. */
export class RepairView extends View {
    createNativeView() {
        return new (com.akylas.documentscanner as any).RepairView(this._context);
    }
    disposeNativeView() {
        this.nativeViewProtected?.release();
        super.disposeNativeView();
    }
}
