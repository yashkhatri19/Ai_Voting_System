/* app-ui.js - Core UI Logic for Theme, Sidebar and OTP inputs */
document.addEventListener('DOMContentLoaded', () => {
    // 1. Mobile Sidebar Toggle
    const sidebarToggle = document.getElementById('sidebarToggle');
    const appSidebar = document.querySelector('.app-sidebar');
    if (sidebarToggle && appSidebar) {
        sidebarToggle.addEventListener('click', () => {
            appSidebar.classList.toggle('show');
        });
    }

    // 2. OTP Inputs Orchestration
    const otpContainer = document.querySelector('.otp-inputs-wrapper');
    const masterOtpInput = document.getElementById('otp'); // Hidden input bound to Thymeleaf field
    if (otpContainer && masterOtpInput) {
        const inputs = otpContainer.querySelectorAll('.otp-digit-field');
        
        const updateMasterValue = () => {
            let val = '';
            inputs.forEach(input => { val += input.value; });
            masterOtpInput.value = val;
        };

        inputs.forEach((input, index) => {
            // Handle keyup / character insertion
            input.addEventListener('input', (e) => {
                const value = e.target.value;
                // If double character inserted, keep only the last one
                if (value.length > 1) {
                    input.value = value.charAt(value.length - 1);
                }
                
                updateMasterValue();
                
                // Focus next input if populated
                if (input.value && index < inputs.length - 1) {
                    inputs[index + 1].focus();
                }
            });

            // Handle backspace navigation
            input.addEventListener('keydown', (e) => {
                if (e.key === 'Backspace') {
                    if (!input.value && index > 0) {
                        inputs[index - 1].focus();
                        inputs[index - 1].value = '';
                        updateMasterValue();
                    } else {
                        input.value = '';
                        updateMasterValue();
                    }
                }
            });

            // Handle paste actions
            input.addEventListener('paste', (e) => {
                e.preventDefault();
                const pasteData = (e.clipboardData || window.clipboardData).getData('text');
                if (pasteData.length === inputs.length) {
                    for (let i = 0; i < inputs.length; i++) {
                        inputs[i].value = pasteData.charAt(i);
                    }
                    updateMasterValue();
                    inputs[inputs.length - 1].focus();
                }
            });
        });
    }
    // 3. Clear URL query parameters after loading the alerts to prevent reappearing on refresh
    if (window.history.replaceState) {
        const url = new URL(window.location.href);
        if (url.searchParams.has('logout') || url.searchParams.has('verified') || url.searchParams.has('error')) {
            url.searchParams.delete('logout');
            url.searchParams.delete('verified');
            url.searchParams.delete('error');
            window.history.replaceState({ path: url.toString() }, '', url.toString());
        }
    }

    // 4. Auto-dismiss alerts after 5 seconds
    const alerts = document.querySelectorAll('.alert-dismissible');
    alerts.forEach(alert => {
        setTimeout(() => {
            const closeButton = alert.querySelector('.btn-close');
            if (closeButton) {
                closeButton.click();
            }
        }, 5000);
    });
});
