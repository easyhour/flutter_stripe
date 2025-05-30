package com.reactnativestripesdk

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.text.InputFilter
import android.view.View
import android.view.View.OnFocusChangeListener
import android.widget.FrameLayout
import androidx.core.view.setMargins
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.uimanager.PixelUtil
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.UIManagerHelper
import com.facebook.react.views.text.ReactTypefaceUtils
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.reactnativestripesdk.utils.PostalCodeUtilities
import com.reactnativestripesdk.utils.getIntOrNull
import com.reactnativestripesdk.utils.getValOr
import com.reactnativestripesdk.utils.hideSoftKeyboard
import com.reactnativestripesdk.utils.mapCardBrand
import com.reactnativestripesdk.utils.mapToPreferredNetworks
import com.reactnativestripesdk.utils.showSoftKeyboard
import com.stripe.android.core.model.CountryCode
import com.stripe.android.databinding.StripeCardFormViewBinding
import com.stripe.android.databinding.StripeCardMultilineWidgetBinding
import com.stripe.android.model.Address
import com.stripe.android.model.PaymentMethodCreateParams
import com.stripe.android.view.CardFormView
import com.stripe.android.view.CardInputListener

@SuppressLint("ViewConstructor")
class CardFormView(
  private val context: ThemedReactContext,
) : FrameLayout(context) {
  internal var cardForm: CardFormView =
    CardFormView(context, null, com.stripe.android.R.style.StripeCardFormView_Borderless)
  private var dangerouslyGetFullCardDetails: Boolean = false
  private var currentFocusedField: String? = null
  var cardParams: PaymentMethodCreateParams.Card? = null
  var cardAddress: Address? = null
  private val cardFormViewBinding = StripeCardFormViewBinding.bind(cardForm)
  private val multilineWidgetBinding =
    StripeCardMultilineWidgetBinding.bind(cardFormViewBinding.cardMultilineWidget)

  init {
    cardFormViewBinding.cardMultilineWidgetContainer.isFocusable = true
    cardFormViewBinding.cardMultilineWidgetContainer.isFocusableInTouchMode = true
    (cardFormViewBinding.cardMultilineWidgetContainer.layoutParams as MarginLayoutParams).setMargins(
      0,
    )
    addView(cardForm)
    setListeners()

    viewTreeObserver.addOnGlobalLayoutListener { requestLayout() }
  }

  fun setPostalCodeEnabled(value: Boolean) {
    val visibility = if (value) View.VISIBLE else View.GONE

    cardFormViewBinding.cardMultilineWidget.postalCodeRequired = false
    cardFormViewBinding.postalCodeContainer.visibility = visibility
  }

  fun setDefaultValues(defaults: ReadableMap) {
    setCountry(defaults.getString("countryCode"))
  }

  fun setDisabled(isDisabled: Boolean) {
    cardForm.isEnabled = !isDisabled
  }

  fun setPreferredNetworks(preferredNetworks: ArrayList<Int>?) {
    cardForm.setPreferredNetworks(mapToPreferredNetworks(preferredNetworks))
  }

  @SuppressLint("RestrictedApi")
  private fun setCountry(countryString: String?) {
    if (countryString != null) {
      cardFormViewBinding.countryLayout.setSelectedCountryCode(CountryCode(countryString))
      cardFormViewBinding.countryLayout.updateUiForCountryEntered(CountryCode(countryString))
    }
    setPostalCodeFilter()
  }

  fun setPlaceHolders(value: ReadableMap) {
    val numberPlaceholder = getValOr(value, "number", null)
    val expirationPlaceholder = getValOr(value, "expiration", null)
    val cvcPlaceholder = getValOr(value, "cvc", null)
    val postalCodePlaceholder = getValOr(value, "postalCode", null)

    numberPlaceholder?.let {
      multilineWidgetBinding.tlCardNumber.hint = it
    }
    expirationPlaceholder?.let {
      multilineWidgetBinding.tlExpiry.hint = it
    }
    cvcPlaceholder?.let {
      multilineWidgetBinding.tlCvc.hint = it
    }
    postalCodePlaceholder?.let {
      cardFormViewBinding.postalCodeContainer.hint = it
    }
  }

  fun setAutofocus(value: Boolean) {
    if (value) {
      val cardNumberEditText = multilineWidgetBinding.etCardNumber
      cardNumberEditText.requestFocus()
      cardNumberEditText.showSoftKeyboard()
    }
  }

  fun requestFocusFromJS() {
    val cardNumberEditText = multilineWidgetBinding.etCardNumber
    cardNumberEditText.requestFocus()
    cardNumberEditText.showSoftKeyboard()
  }

  fun requestBlurFromJS() {
    val cardNumberEditText = multilineWidgetBinding.etCardNumber
    cardNumberEditText.hideSoftKeyboard()
    cardNumberEditText.clearFocus()
  }

  fun requestClearFromJS() {
    multilineWidgetBinding.etCardNumber.setText("")
    multilineWidgetBinding.etCvc.setText("")
    multilineWidgetBinding.etExpiry.setText("")
    cardFormViewBinding.postalCode.setText("")
  }

  private fun onChangeFocus() {
    UIManagerHelper
      .getEventDispatcherForReactTag(context, id)
      ?.dispatchEvent(CardFocusEvent(context.surfaceId, id, currentFocusedField))
  }

  @SuppressLint("RestrictedApi")
  fun setCardStyle(value: ReadableMap) {
    val backgroundColor = getValOr(value, "backgroundColor", null)
    val textColor = getValOr(value, "textColor", null)
    val borderWidth = getIntOrNull(value, "borderWidth")
    val borderColor = getValOr(value, "borderColor", null)
    val borderRadius = getIntOrNull(value, "borderRadius") ?: 0
    val fontSize = getIntOrNull(value, "fontSize")
    val fontFamily = getValOr(value, "fontFamily")
    val placeholderColor = getValOr(value, "placeholderColor", null)
    val textErrorColor = getValOr(value, "textErrorColor", null)
    val cursorColor = getValOr(value, "cursorColor", null)

    val editTextBindings =
      setOf(
        cardFormViewBinding.cardMultilineWidget.cardNumberEditText,
        cardFormViewBinding.cardMultilineWidget.cvcEditText,
        cardFormViewBinding.cardMultilineWidget.expiryDateEditText,
        cardFormViewBinding.postalCode,
      )
    val placeholderTextBindings =
      setOf(
        multilineWidgetBinding.tlExpiry,
        multilineWidgetBinding.tlCardNumber,
        multilineWidgetBinding.tlCvc,
        cardFormViewBinding.postalCodeContainer,
      )

    textColor?.let {
      for (binding in editTextBindings) {
        binding.setTextColor(Color.parseColor(it))
      }
      cardFormViewBinding.countryLayout.countryAutocomplete.setTextColor(Color.parseColor(it))
    }
    textErrorColor?.let {
      for (binding in editTextBindings) {
        binding.setErrorColor(Color.parseColor(it))
        cardFormViewBinding.postalCode.setErrorColor(Color.parseColor(it))
      }
    }
    placeholderColor?.let {
      for (binding in placeholderTextBindings) {
        binding.defaultHintTextColor = ColorStateList.valueOf(Color.parseColor(it))
      }
    }
    fontSize?.let {
      for (binding in editTextBindings) {
        binding.textSize = it.toFloat()
      }
    }
    fontFamily?.let {
      // Load custom font from assets, and fallback to default system font
      val typeface =
        ReactTypefaceUtils.applyStyles(null, -1, -1, it.takeIf { it.isNotEmpty() }, context.assets)
      for (binding in editTextBindings) {
        binding.typeface = typeface
      }
      for (binding in placeholderTextBindings) {
        binding.typeface = typeface
      }
      cardFormViewBinding.countryLayout.typeface = typeface
      cardFormViewBinding.countryLayout.countryAutocomplete.typeface = typeface
      cardFormViewBinding.errors.typeface = typeface
    }
    cursorColor?.let {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val color = Color.parseColor(it)
        for (binding in editTextBindings) {
          binding.textCursorDrawable?.setTint(color)
          binding.textSelectHandle?.setTint(color)
          binding.textSelectHandleLeft?.setTint(color)
          binding.textSelectHandleRight?.setTint(color)
          binding.highlightColor = color
        }
      }
    }

    cardFormViewBinding.cardMultilineWidgetContainer.background =
      MaterialShapeDrawable(
        ShapeAppearanceModel()
          .toBuilder()
          .setAllCorners(CornerFamily.ROUNDED, PixelUtil.toPixelFromDIP(borderRadius.toDouble()))
          .build(),
      ).also { shape ->
        shape.strokeWidth = 0.0f
        shape.strokeColor = ColorStateList.valueOf(Color.parseColor("#000000"))
        shape.fillColor = ColorStateList.valueOf(Color.parseColor("#FFFFFF"))
        borderWidth?.let {
          shape.strokeWidth = PixelUtil.toPixelFromDIP(it.toDouble())
        }
        borderColor?.let {
          shape.strokeColor = ColorStateList.valueOf(Color.parseColor(it))
        }
        backgroundColor?.let {
          shape.fillColor = ColorStateList.valueOf(Color.parseColor(it))
        }
      }
  }

  fun setDangerouslyGetFullCardDetails(isEnabled: Boolean) {
    dangerouslyGetFullCardDetails = isEnabled
  }

  private fun setListeners() {
    cardForm.setCardValidCallback { isValid, _ ->
      if (isValid) {
        cardForm.cardParams?.let {
          val cardParamsMap = it.toParamMap()["card"] as HashMap<*, *>
          val cardDetails: MutableMap<String, Any> =
            mutableMapOf(
              "expiryMonth" to cardParamsMap["exp_month"] as Int,
              "expiryYear" to cardParamsMap["exp_year"] as Int,
              "last4" to it.last4,
              "brand" to mapCardBrand(it.brand),
              "postalCode" to (it.address?.postalCode ?: ""),
              "country" to (it.address?.country ?: ""),
            )

          if (dangerouslyGetFullCardDetails) {
            cardDetails["number"] = cardParamsMap["number"] as String
            cardDetails["cvc"] = cardParamsMap["cvc"] as String
          }

          UIManagerHelper
            .getEventDispatcherForReactTag(context, id)
            ?.dispatchEvent(
              CardFormCompleteEvent(
                context.surfaceId,
                id,
                cardDetails,
                isValid,
                dangerouslyGetFullCardDetails,
              ),
            )

          cardAddress =
            Address
              .Builder()
              .setPostalCode(it.address?.postalCode)
              .setCountry(it.address?.country)
              .build()

          cardFormViewBinding.cardMultilineWidget.paymentMethodCard?.let { params ->
            cardParams = params
          }
        }
      } else {
        cardParams = null
        cardAddress = null
        UIManagerHelper
          .getEventDispatcherForReactTag(context, id)
          ?.dispatchEvent(
            CardFormCompleteEvent(
              context.surfaceId,
              id,
              null,
              isValid,
              dangerouslyGetFullCardDetails,
            ),
          )
      }
    }

    val cardNumberEditText = multilineWidgetBinding.etCardNumber
    val cvcEditText = multilineWidgetBinding.etCvc
    val expiryEditText = multilineWidgetBinding.etExpiry
    val postalCodeEditText = cardFormViewBinding.postalCode

    cardNumberEditText.onFocusChangeListener =
      OnFocusChangeListener { _, hasFocus ->
        currentFocusedField =
          if (hasFocus) CardInputListener.FocusField.CardNumber.toString() else null
        onChangeFocus()
      }
    cvcEditText.onFocusChangeListener =
      OnFocusChangeListener { _, hasFocus ->
        currentFocusedField = if (hasFocus) CardInputListener.FocusField.Cvc.toString() else null
        onChangeFocus()
      }
    expiryEditText.onFocusChangeListener =
      OnFocusChangeListener { _, hasFocus ->
        currentFocusedField =
          if (hasFocus) CardInputListener.FocusField.ExpiryDate.toString() else null
        onChangeFocus()
      }
    postalCodeEditText.onFocusChangeListener =
      OnFocusChangeListener { _, hasFocus ->
        currentFocusedField =
          if (hasFocus) CardInputListener.FocusField.PostalCode.toString() else null
        onChangeFocus()
      }
  }

  private fun setPostalCodeFilter() {
    cardFormViewBinding.postalCode.filters =
      arrayOf(
        *cardFormViewBinding.postalCode.filters,
        createPostalCodeInputFilter(),
      )
  }

  @SuppressLint("RestrictedApi")
  private fun createPostalCodeInputFilter(): InputFilter {
    return InputFilter { charSequence, start, end, _, _, _ ->
      if (cardFormViewBinding.countryLayout.getSelectedCountryCode() == CountryCode.US) {
        // Rely on CardFormView's built-in US postal code filter
        return@InputFilter null
      }

      for (i in start until end) {
        if (!PostalCodeUtilities.isValidGlobalPostalCodeCharacter(charSequence[i])) {
          return@InputFilter ""
        }
      }
      return@InputFilter null
    }
  }

  override fun requestLayout() {
    super.requestLayout()
    post(mLayoutRunnable)
  }

  private val mLayoutRunnable =
    Runnable {
      measure(
        MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
      )
      layout(left, top, right, bottom)
    }
}
