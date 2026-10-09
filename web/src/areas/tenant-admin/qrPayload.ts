export function qrPayload(base: string | undefined, token: string) { return base ? `${base.replace(/\/+$/, '')}/t/${token}` : undefined }
