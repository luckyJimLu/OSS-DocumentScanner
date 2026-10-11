<script context="module" lang="ts">
    import { registerNativeViewElement } from '@nativescript-community/svelte-native/dom';
    import { RepairView } from 'plugin-nativeprocessor/RepairView';
    registerNativeViewElement('repairCanvas', () => RepairView);
</script>

<script lang="ts">
    import { Color } from '@nativescript/core';
    import { confirm } from '@nativescript-community/ui-material-dialogs';
    import { closeModal } from '@shared/utils/svelte/ui';
    import { showError } from '@shared/utils/showError';
    import CActionBar from '~/components/common/CActionBar.svelte';
    import { lc } from '~/helpers/locale';
    import { OCRDocument } from '~/models/OCRDocument';
    import { getPageColorMatrix } from '~/utils/matrix';
    import { colors, windowInset } from '~/variables';

    export let document: OCRDocument;
    export let pageIndex: number;
    const item = document.pages[pageIndex];
    let page;
    let nativeCanvas: any;
    let busy = true;
    let saving = false;
    let drawing = false;
    let undos = 0;
    let redos = 0;
    let brushSize = 20;
    let comparing = false;
    let errorMessage = '';
    let confirming = false;
    $: nativeCanvas?.setBrushSize(brushSize);
    $: nativeCanvas?.setComparing(comparing);

    function loaded(event) {
        if (nativeCanvas) return;
        nativeCanvas = event.object.nativeViewProtected;
        const mask = new Color($colors.colorPrimary);
        nativeCanvas.setMaskColor(android.graphics.Color.argb(100, mask.r, mask.g, mask.b));
        nativeCanvas.setColorMatrix(JSON.stringify(getPageColorMatrix(item) || []));
        nativeCanvas.setListener(
            new com.akylas.documentscanner.utils.FunctionCallback({
                onResult(error, value) {
                    if (error) {
                        busy = false;
                        errorMessage = lc('repair_failed');
                        showError(error instanceof Error ? error : new Error(String(error)));
                    } else {
                        const state = JSON.parse(value);
                        busy = state.busy;
                        drawing = state.drawing;
                        undos = state.undos;
                        redos = state.redos;
                    }
                }
            })
        );
        nativeCanvas.loadImage(item.imagePath, item.rotation || 0);
    }

    async function cancel() {
        if (saving || confirming) return;
        if (undos || drawing) {
            confirming = true;
            try {
                const discard = await confirm({
                    title: lc('repair_discard_title'),
                    message: lc('repair_discard_message'),
                    okButtonText: lc('repair_discard'),
                    cancelButtonText: lc('repair_continue')
                });
                if (!discard) return;
            } finally {
                confirming = false;
            }
        }
        closeModal();
    }

    async function save() {
        if (busy || drawing || saving || !undos) return;
        saving = true;
        errorMessage = '';
        try {
            await document.repairPage(pageIndex, JSON.parse(nativeCanvas.getStrokes()));
            closeModal(true);
        } catch (error) {
            errorMessage = lc('repair_save_failed');
            showError(error instanceof Error ? error : new Error(String(error)));
        } finally {
            saving = false;
        }
    }

    function back(event) {
        event.cancel = true;
        cancel();
    }
</script>

<page bind:this={page} actionBarHidden={true} on:activityBackPressed={back}>
    <gridlayout class="pageContent" rows="auto,*,auto,auto,auto" android:paddingBottom={$windowInset.bottom}>
        <CActionBar title={lc('repair')} modalWindow={true} onGoBack={cancel} disableBackButton={saving}>
            <mdbutton text={lc('save')} accessibilityLabel={lc('save')} isEnabled={!busy && !drawing && !saving && undos > 0} variant="text" on:tap={save} />
        </CActionBar>
        <repairCanvas row={1} isEnabled={!saving} on:loaded={loaded} accessibilityLabel={lc('repair_hint')} />
        {#if busy || saving}
            <activityindicator row={1} busy={true} horizontalAlignment="center" verticalAlignment="center" />
        {/if}
        <label
            row={2}
            text={errorMessage || (saving ? lc('repair_saving') : busy ? lc('computing') : comparing ? lc('repair_original') : lc('repair_hint'))}
            textWrap={true}
            textAlignment="center"
            fontSize={13}
            padding="8 16"
        />
        <gridlayout row={3} columns="auto,*,auto" padding="0 16">
            <label text={lc('repair_brush')} verticalAlignment="center" />
            <mdslider
                col={1}
                minValue={4}
                maxValue={60}
                value={brushSize}
                isEnabled={!busy && !saving && !comparing}
                accessibilityLabel={lc('repair_brush')}
                on:valueChange={(e) => (brushSize = Math.round(e.value))}
            />
            <label col={2} text={`${brushSize}`} width={28} textAlignment="right" verticalAlignment="center" />
        </gridlayout>
        <gridlayout row={4} columns="*,*,*,*" padding="0 8">
            <mdbutton class="icon-btn" text="mdi-undo" accessibilityLabel={lc('undo')} isEnabled={!busy && !saving && !comparing && undos > 0} variant="text" on:tap={() => nativeCanvas.undo()} />
            <mdbutton
                col={1}
                class="icon-btn"
                text="mdi-redo"
                accessibilityLabel={lc('redo')}
                isEnabled={!busy && !saving && !comparing && redos > 0}
                variant="text"
                on:tap={() => nativeCanvas.redo()}
            />
            <mdbutton
                col={2}
                class="icon-btn"
                text={comparing ? 'mdi-eye-off' : 'mdi-eye'}
                accessibilityLabel={lc('repair_compare')}
                isEnabled={!busy && !saving}
                variant="text"
                on:tap={() => (comparing = !comparing)}
            />
            <mdbutton col={3} class="icon-btn" text="mdi-fit-to-screen" accessibilityLabel={lc('repair_fit')} isEnabled={!busy && !saving} variant="text" on:tap={() => nativeCanvas.resetZoom()} />
        </gridlayout>
    </gridlayout>
</page>
