<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';

import { ProfileNotificationSetting } from '@vben/common-ui';
import { useUserStore } from '@vben/stores';

import { updateNotificationApi } from '#/api';

const userStore = useUserStore();

const emailNotification = ref(true);

const formSchema = computed(() => {
  return [
    {
      value: emailNotification.value,
      fieldName: 'emailNotification',
      label: '邮件消息通知',
      description: '开启后将通过邮件接收消息通知',
    },
  ];
});

onMounted(() => {
  emailNotification.value = userStore.userInfo?.emailNotification ?? true;
});

async function handleChange({ value }: { fieldName: string; value: boolean }) {
  emailNotification.value = value;
  await updateNotificationApi({ emailNotification: value });
}
</script>
<template>
  <ProfileNotificationSetting
    :form-schema="formSchema"
    @change="handleChange"
  />
</template>
