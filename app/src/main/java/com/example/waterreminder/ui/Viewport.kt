package com.example.waterreminder.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * 矮视口判断：MuMu 模拟器（1080×1920 @ 480DPI）折算后只有 360×640dp，
 * 小屏手机也普遍在这个量级。此时固定的大尺寸主视觉会占满一屏，
 * 需要整体收紧；常规手机（高度 ≥ 700dp）维持原设计。
 */
@Composable
internal fun isCompactViewport(): Boolean =
    LocalConfiguration.current.screenHeightDp < 700
