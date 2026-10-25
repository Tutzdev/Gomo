import { createBrowserRouter, Navigate } from "react-router-dom";
import { RouteLoadingPage } from "@/app/route-loading-page";

const hydrateFallbackElement = <RouteLoadingPage />;

export const router = createBrowserRouter([
  {
    path: "/",
    hydrateFallbackElement,
    lazy: () => import("@/routes/landing-page").then(({ LandingPage }) => ({ Component: LandingPage })),
  },
  {
    path: "/entrar",
    hydrateFallbackElement,
    lazy: () => import("@/features/auth/auth-page").then(({ AuthPage }) => ({ Component: AuthPage })),
  },
  {
    path: "/recuperar-senha",
    hydrateFallbackElement,
    lazy: () => import("@/features/auth/auth-page").then(({ PasswordResetRequestPage }) => ({
      Component: PasswordResetRequestPage,
    })),
  },
  {
    path: "/redefinir-senha",
    hydrateFallbackElement,
    lazy: () => import("@/features/auth/auth-page").then(({ PasswordResetConfirmPage }) => ({
      Component: PasswordResetConfirmPage,
    })),
  },
  {
    path: "/confirmar-email",
    hydrateFallbackElement,
    lazy: () => import("@/features/auth/auth-page").then(({ EmailConfirmationPage }) => ({
      Component: EmailConfirmationPage,
    })),
  },
  {
    path: "/assinar",
    hydrateFallbackElement,
    lazy: () => import("@/routes/subscription-page").then(({ SubscriptionPage }) => ({
      Component: SubscriptionPage,
    })),
  },
  {
    path: "/termos",
    hydrateFallbackElement,
    lazy: () => import("@/routes/legal-pages").then(({ TermsPage }) => ({ Component: TermsPage })),
  },
  {
    path: "/privacidade",
    hydrateFallbackElement,
    lazy: () => import("@/routes/legal-pages").then(({ PrivacyPage }) => ({ Component: PrivacyPage })),
  },
  {
    path: "/precos/:slug",
    hydrateFallbackElement,
    lazy: () => import("@/routes/product-price-page").then(({ ProductPricePage }) => ({ Component: ProductPricePage })),
  },
  {
    path: "/acesso-negado",
    hydrateFallbackElement,
    lazy: () => import("@/routes/error-pages").then(({ AccessDeniedPage }) => ({
      Component: AccessDeniedPage,
    })),
  },
  {
    path: "/app",
    hydrateFallbackElement,
    lazy: () => import("@/app/route-guards").then(({ AppLayout }) => ({ Component: AppLayout })),
    // Searching, comparing and the markets are open to visitors; everything else needs an account.
    children: [
      {
        // Searching and comparing are one page now; old links without a product land on the search.
        path: "comparar",
        element: <Navigate to="/app/produtos" replace />,
      },
      {
        path: "comparar/:id",
        lazy: () => import("@/routes/app/comparison-page").then(({ ComparisonPage }) => ({
          Component: ComparisonPage,
        })),
      },
      {
        path: "produtos",
        lazy: () => import("@/routes/app/products-page").then(({ ProductsPage }) => ({ Component: ProductsPage })),
      },
      {
        path: "produtos/:id",
        lazy: () => import("@/routes/app/product-detail-page").then(({ ProductDetailPage }) => ({
          Component: ProductDetailPage,
        })),
      },
      {
        path: "supermercados",
        lazy: () => import("@/routes/app/stores-page").then(({ StoresPage }) => ({ Component: StoresPage })),
      },
      {
        path: "supermercados/:id",
        lazy: () => import("@/routes/app/store-catalog-page").then(({ StoreCatalogPage }) => ({ Component: StoreCatalogPage })),
      },
      {
        lazy: () => import("@/app/route-guards").then(({ AccountGuard }) => ({ Component: AccountGuard })),
        children: [
          {
            index: true,
            lazy: () => import("@/routes/app/overview-page").then(({ OverviewPage }) => ({ Component: OverviewPage })),
          },
          {
            path: "listas",
            lazy: () => import("@/routes/app/shopping-lists-page").then(({ ShoppingListsPage }) => ({
              Component: ShoppingListsPage,
            })),
          },
          {
            path: "listas/:id",
            lazy: () => import("@/routes/app/shopping-list-detail-page").then(({ ShoppingListDetailPage }) => ({
              Component: ShoppingListDetailPage,
            })),
          },
          {
            path: "alertas",
            lazy: () => import("@/routes/app/alerts-page").then(({ AlertsPage }) => ({ Component: AlertsPage })),
          },
          {
            path: "contribuir",
            lazy: () => import("@/routes/app/contributions-page").then(({ ContributionsPage }) => ({
              Component: ContributionsPage,
            })),
          },
          {
            path: "notificacoes",
            lazy: () => import("@/routes/app/notifications-page").then(({ NotificationsPage }) => ({
              Component: NotificationsPage,
            })),
          },
          {
            path: "perfil",
            lazy: () => import("@/routes/app/profile-page").then(({ ProfilePage }) => ({ Component: ProfilePage })),
          },
          {
            lazy: () => import("@/app/route-guards").then(({ AdminGuard }) => ({ Component: AdminGuard })),
            children: [
              {
                path: "admin",
                lazy: () => import("@/routes/app/admin-page").then(({ AdminPage }) => ({ Component: AdminPage })),
              },
            ],
          },
        ],
      },
    ],
  },
  {
    path: "*",
    hydrateFallbackElement,
    lazy: () => import("@/routes/error-pages").then(({ NotFoundPage }) => ({ Component: NotFoundPage })),
  },
]);
