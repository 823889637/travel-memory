<template>
  <div class="app-shell">
    <header class="topbar">
      <RouterLink class="brand" to="/">Travel Memory</RouterLink>
      <nav>
        <RouterLink to="/">旅行列表</RouterLink>
        <RouterLink to="/trips/new">创建旅行</RouterLink>
      </nav>
    </header>

    <main class="page">
      <RouterView />
    </main>
  </div>
</template>
