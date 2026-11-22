import { createI18n } from "vue-i18n";

/** zh 前缀（zh-CN/zh-TW/zh-HK/zh-SG…）全归 zh-CN，其余归 en-US */
function detectLocale(): string {
  const language = navigator.language ?? "";
  return language.toLowerCase().startsWith("zh") ? "zh-CN" : "en-US";
}

export const i18n = createI18n({
  legacy: false,
  locale: detectLocale(),
  fallbackLocale: "en-US",
  messages: {
    "zh-CN": {
      shell: {
        dialog: {
          confirm: "确认",
          cancel: "取消",
        },
      },
    },
    "en-US": {
      shell: {
        dialog: {
          confirm: "Confirm",
          cancel: "Cancel",
        },
      },
    },
  },
});
