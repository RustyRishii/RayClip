import { showToast, Toast } from "@raycast/api";
import { pullLatest } from "./rayclip";

export default async function Command() {
  try {
    await pullLatest();
  } catch (error) {
    await showToast({
      style: Toast.Style.Failure,
      title: "Failed to pull clipboard",
      message: error instanceof Error ? error.message : String(error)
    });
  }
}
