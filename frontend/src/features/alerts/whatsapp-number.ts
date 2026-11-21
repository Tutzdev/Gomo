const MOBILE_WITH_DDD = /^(?:55)?[1-9]{2}9\d{8}$/;

/** Same rule as the server: a Brazilian mobile with DDD, typed in any format, with or without +55. */
export function isMobileWithDdd(typed: string) {
  return MOBILE_WITH_DDD.test(typed.replace(/\D/g, ""));
}

/** "+5524999998888" → "(24) 99999-8888". */
export function formatWhatsAppNumber(international: string) {
  const digits = international.replace(/\D/g, "").slice(2);

  return `(${digits.slice(0, 2)}) ${digits.slice(2, 7)}-${digits.slice(7)}`;
}
