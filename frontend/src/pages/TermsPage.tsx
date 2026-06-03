export function TermsPage() {
  return (
    <div className="mx-auto w-full max-w-2xl px-4 py-10">
      <h1 className="mb-2 text-3xl font-semibold text-forest-900">Terms of Service</h1>
      <p className="mb-8 text-sm text-forest-500">Last updated: June 2, 2026</p>

      <div className="prose prose-sm max-w-none text-forest-700">
        <p>By using CampAlert ("the Service"), you agree to these Terms of Service. Please read them carefully.</p>

        <h2 className="mt-8 text-lg font-semibold text-forest-900">1. Use of the Service</h2>
        <p>
          CampAlert monitors Recreation.gov campground availability and sends notifications when sites become available.
          You must create an account and provide a verified phone number to use alert features.
        </p>

        <h2 className="mt-8 text-lg font-semibold text-forest-900" id="sms-program">
          2. SMS Notification Program
        </h2>
        <p>By adding and verifying a phone number, you consent to receive SMS text messages from CampAlert.</p>
        <ul className="mt-2 list-disc pl-5">
          <li>
            <strong>Program name:</strong> CampAlert Availability Alerts
          </li>
          <li>
            <strong>Message types:</strong> Campsite availability notifications for campgrounds you are watching
          </li>
          <li>
            <strong>Message frequency:</strong> Messages are sent only when a matching site becomes available. Frequency
            depends on how many alerts you have active and how often sites open.
          </li>
          <li>
            <strong>Message and data rates may apply.</strong> Standard carrier rates for SMS and data apply. CampAlert
            does not charge separately for messages.
          </li>
          <li>
            <strong>To stop receiving messages:</strong> Reply <strong>STOP</strong> to any message. You will receive a
            confirmation and no further messages will be sent to that number.
          </li>
          <li>
            <strong>To re-enable messages:</strong> Reply <strong>UNSTOP</strong> or <strong>START</strong> to restore
            delivery, then verify your number again in account settings.
          </li>
          <li>
            <strong>For help:</strong> Reply <strong>HELP</strong> or contact us at support@campfinder.app.
          </li>
          <li>
            <strong>Carrier disclaimer:</strong> Carriers are not liable for delayed or undelivered messages.
          </li>
        </ul>
        <p className="mt-3">
          For details on how we handle your phone number data, see our{' '}
          <a href="/privacy#phone-data" className="text-forest-800 underline hover:text-forest-900">
            Privacy Policy
          </a>
          .
        </p>

        <h2 className="mt-8 text-lg font-semibold text-forest-900">3. Account Responsibilities</h2>
        <p>
          You are responsible for maintaining the security of your account credentials. You must provide accurate
          information and must not use the Service for any unlawful purpose.
        </p>

        <h2 className="mt-8 text-lg font-semibold text-forest-900">4. Availability and Accuracy</h2>
        <p>
          CampAlert relies on Recreation.gov availability data. We do not guarantee that sites will still be available
          by the time you complete a reservation. The Service is provided "as is" without warranties of any kind.
        </p>

        <h2 className="mt-8 text-lg font-semibold text-forest-900">5. Modifications</h2>
        <p>
          We may update these Terms at any time. Continued use of the Service after changes constitutes acceptance of
          the updated Terms.
        </p>

        <h2 className="mt-8 text-lg font-semibold text-forest-900">6. Contact</h2>
        <p>
          Questions? Email us at{' '}
          <a href="mailto:support@campfinder.app" className="text-forest-800 underline">
            support@campfinder.app
          </a>
          .
        </p>
      </div>
    </div>
  )
}
