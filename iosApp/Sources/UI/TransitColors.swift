// TransitColors.swift — цвета маршрутов и общие элементы оформления.

import SwiftUI
import UIKit
import NorilskTransitCore

extension UIColor {
    /// Цвет маршрута из JSON: 0xAARRGGBB.
    convenience init(argb: UInt32) {
        self.init(
            red: CGFloat((argb >> 16) & 0xFF) / 255.0,
            green: CGFloat((argb >> 8) & 0xFF) / 255.0,
            blue: CGFloat(argb & 0xFF) / 255.0,
            alpha: CGFloat((argb >> 24) & 0xFF) / 255.0
        )
    }
}

extension Color {
    init(argb: UInt32) {
        self.init(uiColor: UIColor(argb: argb))
    }
}

/// Плашка с номером маршрута — как в Android-версии.
struct RouteBadge: View {
    let number: String
    let colorArgb: UInt32
    var height: CGFloat = 44

    var body: some View {
        Text(number)
            .font(.system(size: height * 0.42, weight: .black))
            .foregroundStyle(foreground)
            .padding(.horizontal, 8)
            .frame(minWidth: height, minHeight: height)
            .background(Color(argb: colorArgb), in: RoundedRectangle(cornerRadius: height / 4.5))
            .overlay(
                RoundedRectangle(cornerRadius: height / 4.5)
                    .strokeBorder(.black.opacity(0.35), lineWidth: 1)
            )
    }

    /// Простая эвристика читаемости: на светлом фоне — чёрный текст.
    private var foreground: Color {
        let r = Double((colorArgb >> 16) & 0xFF) / 255.0
        let g = Double((colorArgb >> 8) & 0xFF) / 255.0
        let b = Double(colorArgb & 0xFF) / 255.0
        let luma = 0.299 * r + 0.587 * g + 0.114 * b
        return luma > 0.6 ? .black : .white
    }
}

/// Пустое состояние / ошибка загрузки данных.
struct PlaceholderStateView: View {
    let systemImage: String
    let title: String
    let message: String?

    var body: some View {
        VStack(spacing: 10) {
            Image(systemName: systemImage)
                .font(.largeTitle)
                .foregroundStyle(.secondary)
            Text(title).font(.headline)
            if let message {
                Text(message)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
        }
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
