// Mobile menu toggle
document.addEventListener('DOMContentLoaded', function() {
  const burger = document.querySelector('.navbar-burger');
  const overlay = document.querySelector('.mobile-menu-overlay');
  const mobileMenu = document.querySelector('.mobile-menu-content');
  const closeButton = document.querySelector('.mobile-menu-close');
  const body = document.body;

  if (!burger || !overlay || !mobileMenu) {
    return; // Elements not present on this page
  }

  function toggleMenu() {
    burger.classList.toggle('active');
    overlay.classList.toggle('active');
    mobileMenu.classList.toggle('active');
    body.style.overflow = mobileMenu.classList.contains('active') ? 'hidden' : '';
  }

  function closeMenu() {
    burger.classList.remove('active');
    overlay.classList.remove('active');
    mobileMenu.classList.remove('active');
    body.style.overflow = '';
  }

  burger.addEventListener('click', toggleMenu);
  overlay.addEventListener('click', closeMenu);

  if (closeButton) {
    closeButton.addEventListener('click', closeMenu);
  }

  // Mobile dropdown toggles
  const dropdowns = mobileMenu.querySelectorAll('.dropdown-toggle');
  dropdowns.forEach(toggle => {
    toggle.addEventListener('click', function(e) {
      e.preventDefault();
      this.classList.toggle('active');
      const menu = this.nextElementSibling;
      if (menu && menu.classList.contains('dropdown-menu')) {
        menu.classList.toggle('active');
      }
    });
  });

  // Close menu on link click (but not dropdown toggles or search)
  const mobileLinks = mobileMenu.querySelectorAll('a:not(.dropdown-toggle)');
  mobileLinks.forEach(link => {
    link.addEventListener('click', () => {
      if (mobileMenu.classList.contains('active')) {
        closeMenu();
      }
    });
  });

  // Handle search trigger in mobile menu
  const mobileSearchTrigger = mobileMenu.querySelector('.search-trigger-mobile');
  if (mobileSearchTrigger) {
    mobileSearchTrigger.addEventListener('click', function(e) {
      e.preventDefault();
      closeMenu();
      // Trigger the main search modal
      setTimeout(() => {
        const searchTrigger = document.querySelector('.search-trigger');
        if (searchTrigger) {
          searchTrigger.click();
        }
      }, 300); // Wait for menu close animation
    });
  }
});
