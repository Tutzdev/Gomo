import { apiRequest, queryString } from "@/lib/api";
import type {
  AdminAudit,
  AlertNotification,
  AuthResponse,
  City,
  PageResponse,
  PriceAlert,
  PriceContribution,
  PriceRecord,
  Product,
  ProductSearchFacets,
  ProductComparison,
  ProductOffers,
  ProductDiscoveryResult,
  ShoppingList,
  ShoppingListComparison,
  ShoppingListItem,
  ShoppingRecommendation,
  ShoppingListSummary,
  State,
  StockAvailability,
  Store,
  StoreProduct,
  User,
  UserPreference,
} from "@/types/api";

export interface ProductFilters {
  query?: string;
  brand?: string;
  gtin?: string;
  category?: string;
  page?: number;
  size?: number;
  storeId?: string;
  unit?: string;
  quantity?: number;
  sort?: string;
}

export const authApi = {
  login: (input: { email: string; password: string }) =>
    apiRequest<AuthResponse>("/auth/login", {
      method: "POST",
      body: input,
      authenticated: false,
    }),
  register: (input: { name: string; email: string; password: string }) =>
    apiRequest<User>("/auth/register", {
      method: "POST",
      body: input,
      authenticated: false,
    }),
  logout: () => apiRequest<void>("/auth/logout", { method: "POST" }),
  currentUser: () => apiRequest<User>("/users/me"),
  updateUser: (name: string) =>
    apiRequest<User>("/users/me", { method: "PATCH", body: { name } }),
  requestEmailVerification: () =>
    apiRequest<void>("/auth/email-verifications", { method: "POST" }),
  confirmEmail: (token: string) =>
    apiRequest<void>("/auth/email-verifications/confirm", {
      method: "POST",
      body: { token },
      authenticated: false,
    }),
  requestPasswordReset: (email: string) =>
    apiRequest<void>("/auth/password-resets", {
      method: "POST",
      body: { email },
      authenticated: false,
    }),
  resetPassword: (token: string, password: string) =>
    apiRequest<void>("/auth/password-resets/confirm", {
      method: "POST",
      body: { token, password },
      authenticated: false,
    }),
};

export const catalogApi = {
  discoverProducts: (filters: ProductFilters & { cityId?: string; storeIds?: string[] } = {}, signal?: AbortSignal) =>
    apiRequest<PageResponse<ProductDiscoveryResult>>(
      `/products/discovery?${queryString({ ...filters, storeIds: filters.storeIds?.join(",") })}`,
      { authenticated: false, signal },
    ),
  states: (page = 0, size = 100) =>
    apiRequest<PageResponse<State>>(`/states?${queryString({ page, size })}`, {
      authenticated: false,
    }),
  cities: (stateId?: string, page = 0, size = 100) =>
    apiRequest<PageResponse<City>>(
      `/cities?${queryString({ stateId, page, size })}`,
      {
        authenticated: false,
      },
    ),
  products: (filters: ProductFilters = {}, signal?: AbortSignal) =>
    apiRequest<PageResponse<Product>>(
      `/products?${queryString({ size: 20, ...filters })}`,
      {
        authenticated: false,
        signal,
      },
    ),
  productFilters: (query: string, signal?: AbortSignal) =>
    apiRequest<ProductSearchFacets>(
      `/products/filters?${queryString({ query })}`,
      { authenticated: false, signal },
    ),
  product: (id: string) =>
    apiRequest<Product>(`/products/${id}`, { authenticated: false }),
  offers: (
    ids: string[],
    cityId: string,
    signal?: AbortSignal,
    storeIds: string[] = [],
  ) =>
    apiRequest<ProductOffers[]>(
      `/products/offers?${queryString({ ids: ids.join(","), cityId, storeIds: storeIds.join(",") })}`,
      { authenticated: false, signal },
    ),
  stores: (cityId?: string, page = 0, size = 100) =>
    apiRequest<PageResponse<Store>>(
      `/stores?${queryString({ cityId, page, size })}`,
      {
        authenticated: false,
      },
    ),
  allStores: async (cityId: string) => {
    const first = await catalogApi.stores(cityId);
    const stores = [...first.content];
    for (let page = 1; page < first.totalPages; page++) {
      stores.push(...(await catalogApi.stores(cityId, page)).content);
    }
    return stores;
  },
  compareAllProductOffers: async (
    productId: string,
    cityId: string,
    storeIds: string[] = [],
  ) => {
    const first = await catalogApi.compareProduct(
      productId,
      cityId,
      0,
      100,
      storeIds,
    );
    const content = [...first.stores.content];
    for (let page = 1; page < first.stores.totalPages; page++) {
      content.push(
        ...(
          await catalogApi.compareProduct(
            productId,
            cityId,
            page,
            100,
            storeIds,
          )
        ).stores.content,
      );
    }
    return { ...first, stores: { ...first.stores, content } };
  },
  store: (id: string) =>
    apiRequest<Store>(`/stores/${id}`, { authenticated: false }),
  storeProducts: (id: string, query: string, page = 0) =>
    apiRequest<PageResponse<StoreProduct>>(
      `/stores/${id}/products?${queryString({ query, page, size: 30 })}`,
      { authenticated: false },
    ),
  priceHistory: (productId: string, storeId: string, page = 0, size = 20) =>
    apiRequest<PageResponse<PriceRecord>>(
      `/prices?${queryString({ productId, storeId, page, size })}`,
      { authenticated: false },
    ),
  compareProduct: (
    productId: string,
    cityId: string,
    page = 0,
    size = 100,
    storeIds: string[] = [],
  ) =>
    apiRequest<ProductComparison>(
      `/comparisons/products?${queryString({ productId, cityId, page, size, storeIds: storeIds.join(",") })}`,
      { authenticated: false },
    ),
};

export const preferenceApi = {
  get: () => apiRequest<UserPreference>("/users/me/preferences"),
  setCity: (cityId: string) =>
    apiRequest<UserPreference>("/users/me/preferences/preferred-city", {
      method: "PUT",
      body: { cityId },
    }),
  clearCity: () =>
    apiRequest<UserPreference>("/users/me/preferences/preferred-city", {
      method: "DELETE",
    }),
  addFavoriteStore: (storeId: string) =>
    apiRequest<UserPreference>(
      `/users/me/preferences/favorite-stores/${storeId}`,
      { method: "PUT" },
    ),
  removeFavoriteStore: (storeId: string) =>
    apiRequest<UserPreference>(
      `/users/me/preferences/favorite-stores/${storeId}`,
      { method: "DELETE" },
    ),
};

export const shoppingListApi = {
  list: (page = 0, size = 20) =>
    apiRequest<PageResponse<ShoppingListSummary>>(
      `/shopping-lists?${queryString({ page, size })}`,
    ),
  get: (id: string) => apiRequest<ShoppingList>(`/shopping-lists/${id}`),
  create: (input: {
    name: string;
    shoppingType: "DAILY" | "WEEKLY" | "MONTHLY" | "CUSTOM";
  }) =>
    apiRequest<ShoppingList>("/shopping-lists", {
      method: "POST",
      body: input,
    }),
  update: (
    id: string,
    input: {
      name: string;
      shoppingType: "DAILY" | "WEEKLY" | "MONTHLY" | "CUSTOM";
    },
  ) =>
    apiRequest<ShoppingList>(`/shopping-lists/${id}`, {
      method: "PUT",
      body: input,
    }),
  remove: (id: string) =>
    apiRequest<void>(`/shopping-lists/${id}`, { method: "DELETE" }),
  addItem: (id: string, input: { productId: string; quantity: number }) =>
    apiRequest<ShoppingListItem>(`/shopping-lists/${id}/items`, {
      method: "POST",
      body: input,
    }),
  updateItem: (id: string, itemId: string, quantity: number) =>
    apiRequest<ShoppingListItem>(`/shopping-lists/${id}/items/${itemId}`, {
      method: "PUT",
      body: { quantity },
    }),
  removeItem: (id: string, itemId: string) =>
    apiRequest<void>(`/shopping-lists/${id}/items/${itemId}`, {
      method: "DELETE",
    }),
  compare: (
    id: string,
    cityId: string,
    page = 0,
    size = 100,
    storeIds: string[] = [],
  ) =>
    apiRequest<ShoppingListComparison>(
      `/comparisons/shopping-lists/${id}?${queryString({ cityId, page, size, storeIds: storeIds.join(",") })}`,
    ),
  recommendation: (id: string, cityId: string, storeIds: string[] = []) =>
    apiRequest<ShoppingRecommendation>(
      `/comparisons/shopping-lists/${id}/recommendation?${queryString({ cityId, storeIds: storeIds.join(",") })}`,
    ),
};

export const alertApi = {
  list: (page = 0, size = 20) =>
    apiRequest<PageResponse<PriceAlert>>(
      `/alerts?${queryString({ page, size })}`,
    ),
  create: (input: { productId: string; cityId: string; targetPrice: number }) =>
    apiRequest<PriceAlert>("/alerts", { method: "POST", body: input }),
  deactivate: (id: string) =>
    apiRequest<PriceAlert>(`/alerts/${id}/deactivation`, { method: "PATCH" }),
  notifications: (page = 0, size = 20) =>
    apiRequest<PageResponse<AlertNotification>>(
      `/notifications?${queryString({ page, size })}`,
    ),
  markNotificationRead: (id: string) =>
    apiRequest<AlertNotification>(`/notifications/${id}/read`, {
      method: "PATCH",
    }),
};

export interface ContributionInput {
  productId: string;
  storeId: string;
  regularPrice: number;
  promotionalPrice?: number;
  currency: "BRL";
  observedAt: string;
  validUntil?: string;
  promotionValidUntil?: string;
  availability: StockAvailability;
}

export const contributionApi = {
  listMine: (page = 0, size = 20) =>
    apiRequest<PageResponse<PriceContribution>>(
      `/contributions?${queryString({ page, size })}`,
    ),
  submit: (input: ContributionInput) =>
    apiRequest<PriceContribution>("/contributions", {
      method: "POST",
      body: input,
    }),
  moderation: (
    status: "PENDING" | "APPROVED" | "REJECTED" = "PENDING",
    page = 0,
  ) =>
    apiRequest<PageResponse<PriceContribution>>(
      `/admin/contributions?${queryString({ status, page, size: 20 })}`,
    ),
  approve: (id: string) =>
    apiRequest<PriceContribution>(`/admin/contributions/${id}/approval`, {
      method: "POST",
    }),
  reject: (id: string, reason: string) =>
    apiRequest<PriceContribution>(`/admin/contributions/${id}/rejection`, {
      method: "POST",
      body: { reason },
    }),
};

export const adminApi = {
  audit: (page = 0, size = 20) =>
    apiRequest<PageResponse<AdminAudit>>(
      `/admin/audit?${queryString({ page, size })}`,
    ),
  registerSource: (input: {
    code: string;
    name: string;
    baseUrl: string;
    verifiedAt: string;
  }) => apiRequest("/admin/sources", { method: "POST", body: input }),
  ingestChain: (input: unknown) =>
    apiRequest("/admin/chains", { method: "POST", body: input }),
  ingestStore: (input: unknown) =>
    apiRequest("/admin/stores", { method: "POST", body: input }),
  ingestProduct: (input: unknown) =>
    apiRequest("/admin/products", { method: "POST", body: input }),
  recordPrice: (input: unknown) =>
    apiRequest("/admin/prices", { method: "POST", body: input }),
};
