export function PrivacyPage() {
  return (
    <div className="mx-auto w-full max-w-2xl px-4 py-10">
      <h1 className="mb-2 text-3xl font-semibold text-forest-900">Privacy Policy</h1>
      <p className="mb-8 text-sm text-forest-500">Last updated: June 2, 2026</p>

      <div className="prose prose-sm max-w-none text-forest-700">
        <p>
          This Privacy Policy explains how CampAlert ("we", "us") collects, uses, and protects your personal
          information.
        </p>

        <h2 className="mt-8 text-lg font-semibold text-forest-900">1. Information We Collect</h2>
        <p>We collect the information you provide when creating an account and using the Service:</p>
        <ul className="mt-2 list-disc pl-5">
          <li>Email address and hashed password</li>
          <li>Phone numbers you add to your account</li>
          <li>Campground alert preferences (campground ID, dates, group size)</li>
        </ul>

        <h2 className="mt-8 text-lg font-semibold text-forest-900" id="phone-data">
          2. Phone Number Data
        </h2>
        <p>When you add a phone number to receive SMS alerts, we collect and store the following:</p>
        <ul className="mt-2 list-disc pl-5">
          <li>
            <strong>Purpose:</strong> Your phone number is used solely to deliver campsite availability notifications
            that you have opted into. We do not use it for marketing unrelated to the Service.
          </li>
          <li>
            <strong>SMS provider:</strong> Messages are delivered via <strong>Twilio</strong>, which acts as our data
            processor. Twilio processes your phone number to route and deliver messages. Twilio's privacy practices are
            governed by their{' '}
            <a
              href="https://www.twilio.com/en-us/legal/privacy"
              className="text-forest-800 underline"
              target="_blank"
              rel="noopener noreferrer"
            >
              Privacy Policy
            </a>
            .
          </li>
          <li>
            <strong>No third-party marketing sharing:</strong> We do not sell, rent, or share your phone number with
            third parties for their marketing purposes.
          </li>
          <li>
            <strong>Retention:</strong> Your phone number is retained as long as your account is active or until you
            remove it from your account settings. Deleting a phone number permanently removes it from our database.
          </li>
          <li>
            <strong>Consent record:</strong> We record the timestamp at which you provided SMS consent for compliance
            purposes.
          </li>
          <li>
            <strong>Opt-out:</strong> You can opt out at any time by replying <strong>STOP</strong> to any message, or
            by deleting the phone number from your account settings. See our{' '}
            <a href="/terms#sms-program" className="text-forest-800 underline">
              SMS program terms
            </a>{' '}
            for full details.
          </li>
        </ul>

        <h2 className="mt-8 text-lg font-semibold text-forest-900">3. How We Use Your Information</h2>
        <ul className="mt-2 list-disc pl-5">
          <li>To deliver campsite availability SMS alerts you have requested</li>
          <li>To authenticate your account</li>
          <li>To communicate service-related updates</li>
        </ul>

        <h2 className="mt-8 text-lg font-semibold text-forest-900">4. Data Security</h2>
        <p>
          We use industry-standard security practices to protect your data. Passwords are stored as cryptographic hashes
          and are never stored in plaintext.
        </p>

        <h2 className="mt-8 text-lg font-semibold text-forest-900">5. Your Rights</h2>
        <p>
          You may access, correct, or delete your personal information through your account settings or by contacting
          us. To delete your account and all associated data, email{' '}
          <a href="mailto:support@campfinder.app" className="text-forest-800 underline">
            support@campfinder.app
          </a>
          .
        </p>

        <h2 className="mt-8 text-lg font-semibold text-forest-900">6. Contact</h2>
        <p>
          Questions about this policy? Contact us at{' '}
          <a href="mailto:support@campfinder.app" className="text-forest-800 underline">
            support@campfinder.app
          </a>
          .
        </p>
      </div>
    </div>
  )
}
