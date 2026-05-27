<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';

import { useUserStore } from '@vben/stores';

import { ElButton, ElForm, ElFormItem, ElInput, ElMessage } from 'element-plus';

import { updateProfileApi } from '#/api';

const userStore = useUserStore();

const form = reactive({
  email: '',
  phone: '',
});
const saving = ref(false);

onMounted(() => {
  form.email = userStore.userInfo?.email ?? '';
  form.phone = userStore.userInfo?.phone ?? '';
});

async function handleSubmit() {
  saving.value = true;
  try {
    const values = {
      email: form.email || null,
      phone: form.phone || null,
    };
    await updateProfileApi(values);
    if (userStore.userInfo) {
      userStore.userInfo.email = values.email ?? undefined;
      userStore.userInfo.phone = values.phone ?? undefined;
    }
    ElMessage.success('更新成功');
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <div class="security-setting">
    <div class="security-item rounded-lg border p-4">
      <div class="space-y-0.5">
        <span class="text-base font-medium">账户密码</span>
        <p class="text-sm text-muted-foreground">当前密码强度：强</p>
      </div>
    </div>

    <ElForm :model="form" label-width="100px" class="security-form">
      <ElFormItem label="备用邮箱">
        <ElInput v-model="form.email" placeholder="请输入邮箱" />
      </ElFormItem>
      <ElFormItem label="密保手机">
        <ElInput v-model="form.phone" placeholder="请输入手机号" />
      </ElFormItem>
      <ElFormItem>
        <ElButton type="primary" :loading="saving" @click="handleSubmit">
          保存
        </ElButton>
      </ElFormItem>
    </ElForm>
  </div>
</template>

<style scoped>
.security-setting {
  max-width: 480px;
}

.security-item {
  margin-bottom: 24px;
}

.security-form {
  margin-top: 8px;
}
</style>
