<script setup>
import TripCityField from './TripCityField.vue'

defineProps({
  form: { type: Object, required: true },
  idPrefix: { type: String, required: true },
  dateError: { type: String, default: '' },
})
</script>

<template>
  <div class="field trip-field-with-counter">
    <label :for="`${idPrefix}-title`">旅行标题<span class="trip-required-mark" aria-hidden="true">•</span></label>
    <input
      :id="`${idPrefix}-title`"
      v-model="form.title"
      required
      maxlength="100"
      placeholder="例如：天津之旅"
    />
    <small class="trip-field-counter">{{ form.title.length }}/100</small>
  </div>

  <TripCityField
    :id="`${idPrefix}-destination`"
    v-model="form.destination"
    v-model:country="form.destinationCountry"
    v-model:latitude="form.destinationLatitude"
    v-model:longitude="form.destinationLongitude"
  />

  <div class="trip-form-date-grid">
    <div class="field">
      <label :for="`${idPrefix}-start-date`">开始日期 <span class="trip-field-optional">（可选）</span></label>
      <input
        :id="`${idPrefix}-start-date`"
        v-model="form.startDate"
        type="date"
        :max="form.endDate || undefined"
      />
    </div>
    <div class="field">
      <label :for="`${idPrefix}-end-date`">结束日期 <span class="trip-field-optional">（可选）</span></label>
      <input
        :id="`${idPrefix}-end-date`"
        v-model="form.endDate"
        type="date"
        :min="form.startDate || undefined"
      />
    </div>
  </div>
  <p v-if="dateError" class="error trip-date-error">{{ dateError }}</p>

  <div class="field trip-field-with-counter">
    <label :for="`${idPrefix}-description`">一句话描述</label>
    <textarea
      :id="`${idPrefix}-description`"
      v-model="form.description"
      maxlength="500"
      placeholder="这趟旅行最想留住的是什么？"
    ></textarea>
    <small class="trip-field-counter">{{ form.description.length }}/500</small>
  </div>

  <div class="field trip-field-with-counter">
    <label :for="`${idPrefix}-notes`">旅行笔记 <span class="trip-field-optional">（可选）</span></label>
    <textarea
      :id="`${idPrefix}-notes`"
      v-model="form.notes"
      maxlength="1000"
      placeholder="记录这段旅程的灵感、期待，或想去的地方…"
    ></textarea>
    <small class="trip-field-counter">{{ form.notes.length }}/1000</small>
  </div>
</template>
