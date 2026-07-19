export function showToast(message) {
  window.dispatchEvent(new CustomEvent('admin-toast', { detail: message }))
}
