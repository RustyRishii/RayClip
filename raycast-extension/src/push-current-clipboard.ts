import { showToast, Toast } from "@raycast/api";
import { pushCurrentClipboard } from "./rayclip";

export default async function Command() {
  try {
    await pushCurrentClipboard();
  } catch (error) {
    await showToast({
      style: Toast.Style.Failure,
      title: "Failed to push clipboard",
      message: error instanceof Error ? error.message : String(error)
    });
  }
}
